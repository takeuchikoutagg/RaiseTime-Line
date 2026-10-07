import { expect, test } from '@playwright/test'
import { apiAs, chooseFiles, createPost, gif, jpeg, newUser, signup } from './support.js'

// テスト設計書 2.4 ペアワイズ（ブラウザ × 配色 × 画像の形式）で足したケース

// ---------- T-18 配色：ライト・ダークのどちらでも、文字と背景の明るさの差が読める範囲にある ----------

// 文字の色と、その後ろの背景色（透明なら親をたどる）のコントラスト比を、ブラウザの中で計算する（WCAG の式）
async function contrastOf(locator) {
  return locator.evaluate((element) => {
    const parse = (color) => {
      const [r, g, b, a = '1'] = color.match(/[\d.]+/g)
      return { rgb: [Number(r), Number(g), Number(b)], alpha: Number(a) }
    }
    const luminance = ([r, g, b]) => {
      const [lr, lg, lb] = [r, g, b].map((value) => {
        const v = value / 255
        return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
      })
      return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
    }
    let background = null
    for (let node = element; node && !background; node = node.parentElement) {
      const color = parse(getComputedStyle(node).backgroundColor)
      if (color.alpha > 0) background = color.rgb
    }
    // どこにも背景色が無ければ、ブラウザの地の色（ライトは白、ダークは黒に近い色）ではなく body の色を使う
    background ??= parse(getComputedStyle(document.body).backgroundColor).rgb
    const text = luminance(parse(getComputedStyle(element).color).rgb)
    const back = luminance(background)
    return (Math.max(text, back) + 0.05) / (Math.min(text, back) + 0.05)
  })
}

for (const colorScheme of ['light', 'dark']) {
  test.describe(`配色：${colorScheme}`, () => {
    test.use({ colorScheme })

    test('主な文字・リンク・ボタンのコントラスト比が 4.5:1 以上', async ({ page }) => {
      const bob = newUser('E2E配色ボブ')
      const bobApi = await apiAs(bob)
      await createPost(bobApi, '配色を確かめる投稿')
      await bobApi.dispose()
      await signup(page.request, newUser('E2E配色'))

      // ユーザーページ：見出し・@ID（薄い文字）・投稿の本文・フォローボタン・ヘッダー
      await page.goto(`/users/${bob.username}`)
      const profile = page.getByRole('region', { name: 'プロフィール' })
      const post = page.getByRole('article').first()
      await expect(post).toBeVisible()
      // 配色が本当に切り替わっていること（ライトは白、ダークは黒の背景）
      await expect(page.locator('body')).toHaveCSS(
        'background-color',
        colorScheme === 'dark' ? 'rgb(0, 0, 0)' : 'rgb(255, 255, 255)',
      )
      const nav = page.getByRole('navigation', { name: 'メイン' })
      const targets = {
        見出し: page.getByRole('heading', { level: 1 }),
        '@ID（薄い文字）': profile.getByText(`@${bob.username}`),
        投稿の本文: post.getByText('配色を確かめる投稿'),
        フォローするボタン: profile.getByRole('button', { name: 'フォローする' }),
        ヘッダーのリンク: nav.getByRole('link', { name: '検索' }),
        投稿ボタン: nav.getByRole('button', { name: '投稿' }),
        ログアウトボタン: nav.getByRole('button', { name: 'ログアウト' }),
      }
      for (const [name, locator] of Object.entries(targets)) {
        expect(await contrastOf(locator), `${colorScheme}：${name}`).toBeGreaterThanOrEqual(4.5)
      }

      // ログイン画面：文の中のリンク（新規登録）・ログインボタン
      await page.getByRole('button', { name: 'ログアウト' }).click()
      await expect(page).toHaveURL(/\/login$/)
      expect(await contrastOf(page.getByRole('link', { name: '新規登録' })), `${colorScheme}：新規登録リンク`)
        .toBeGreaterThanOrEqual(4.5)
      expect(await contrastOf(page.getByRole('button', { name: 'ログイン' })), `${colorScheme}：ログインボタン`)
        .toBeGreaterThanOrEqual(4.5)
    })
  })
}

// ---------- T-19 画像の形式：JPEG・GIF を、実際のブラウザで選んで投稿できる ----------

// JPEG は送る前にブラウザで作り直し（位置情報を消すため）、GIF はそのまま送る。作り直しの処理はブラウザごとに違いが出やすい
test('JPEG と GIF を選んで投稿でき、どちらも表示される', async ({ page }) => {
  await signup(page.request, newUser('E2E画像形式'))
  await page.goto('/')
  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  const compose = page.getByRole('dialog', { name: '新しい投稿' })

  await chooseFiles(page, () => compose.getByRole('button', { name: /^画像を追加/ }).click(), [
    await jpeg(page, 'photo.jpg'),
    gif('anime.gif'),
  ])
  await expect(compose.getByRole('img')).toHaveCount(2)
  await expect(compose.getByRole('alert')).toBeEmpty()
  await compose.getByLabel('本文').fill('JPEG と GIF の投稿')
  await compose.getByRole('button', { name: '投稿する' }).click()
  await expect(compose).toBeHidden()

  const post = page.getByRole('article').filter({ hasText: 'JPEG と GIF の投稿' })
  const images = post.getByRole('img', { name: /^添付画像/ })
  await expect(images).toHaveCount(2)
  // 選んだ順に、それぞれの形式のまま保存される
  await expect(images.nth(0)).toHaveAttribute('src', /\.jpg$/)
  await expect(images.nth(1)).toHaveAttribute('src', /\.gif$/)
  // 実際に画像として読み込める
  for (const image of await images.all()) {
    await expect.poll(() => image.evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true)
  }

  // 後片付け：投稿を消して、S3 にテスト用の画像を残さない
  await post.getByRole('button', { name: 'メニュー' }).click()
  await page.getByRole('menuitem', { name: '削除' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click()
  await expect(post).toBeHidden()
})
