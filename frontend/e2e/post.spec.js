import { expect, test } from '@playwright/test'
import { apiAs, chooseFiles, createPost, newUser, png, signup, tabKey } from './support.js'

const COLORS = [
  [90, 160, 240],
  [80, 190, 120],
  [240, 140, 80],
  [150, 100, 220],
  [230, 100, 150],
]

// テスト仕様書 SC-02 投稿・SC-05 投稿の編集・削除（F-04・F-03・F-10・F-11）
test('投稿：文字数の上限・画像の枚数・キーボードで画像を選ぶ → 投稿 → 編集 → 削除', async ({ page, browserName }) => {
  const user = newUser('E2E投稿')
  await signup(page.request, user)
  await page.goto('/')

  // SC-02-1 「投稿」でモーダルが開き、本文にフォーカス。空なら投稿できない
  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  const compose = page.getByRole('dialog', { name: '新しい投稿' })
  const body = compose.getByLabel('本文')
  const submit = compose.getByRole('button', { name: '投稿する' })
  await expect(body).toBeFocused()
  await expect(submit).toBeDisabled()

  // SC-02-2 絵文字を含む280文字なら投稿でき、281文字はできない
  await body.fill('あ'.repeat(279) + '😀')
  await expect(compose.getByText('280 / 280')).toBeVisible()
  await expect(submit).toBeEnabled()
  await body.fill('あ'.repeat(280) + '😀')
  await expect(compose.getByText('281 / 280')).toBeVisible()
  await expect(submit).toBeDisabled()

  // SC-02-7 キーボードだけで「画像を追加」へ。SC-02-3 5枚選ぶと4枚までになる
  await body.focus()
  await page.keyboard.press(tabKey(browserName))
  const addImage = compose.getByRole('button', { name: /^画像を追加/ })
  await expect(addImage).toBeFocused()
  await chooseFiles(page, () => page.keyboard.press('Enter'), COLORS.map((c, i) => png(`img${i + 1}.png`, c)))
  await expect(compose.getByRole('img')).toHaveCount(4)
  await expect(compose.getByRole('alert')).toHaveText('画像は4枚まで添付できます')
  await expect(addImage).toBeDisabled()

  // SC-02-4 中身が画像ではないファイルは追加しない
  await compose.getByRole('button', { name: '画像4を外す' }).click()
  await compose.getByRole('button', { name: '画像3を外す' }).click()
  await chooseFiles(page, () => addImage.click(), [
    { name: 'fake.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('これは画像ではありません') },
  ])
  await expect(compose.getByRole('img')).toHaveCount(2)
  await expect(compose.getByRole('alert')).not.toBeEmpty()

  // SC-02-5・6 改行・絵文字・画像2枚で投稿すると、先頭に出る
  await body.fill('E2E の投稿です🧪\n2行目：画像は2枚')
  await submit.click()
  await expect(compose).toBeHidden()
  const post = page.getByRole('article').first()
  await expect(post.getByText('E2E の投稿です🧪\n2行目：画像は2枚')).toBeVisible()
  await expect(post.getByText('たった今')).toBeVisible()
  const images = post.getByRole('img', { name: /^添付画像/ })
  await expect(images).toHaveCount(2)
  const removedImageUrl = await images.nth(1).getAttribute('src')

  // SC-05-2 編集は今の内容で開き、変えていなければ保存できない
  await post.getByRole('button', { name: 'メニュー' }).click()
  await page.getByRole('menuitem', { name: '編集' }).click()
  const edit = page.getByRole('dialog', { name: '投稿を編集' })
  await expect(edit.getByLabel('本文')).toHaveValue('E2E の投稿です🧪\n2行目：画像は2枚')
  await expect(edit.getByRole('button', { name: '保存する' })).toBeDisabled()

  // SC-05-4 変更があるまま閉じると確認し、キャンセルなら閉じない
  await edit.getByLabel('本文').fill('編集した本文')
  page.once('dialog', (dialog) => dialog.dismiss())
  await edit.getByRole('button', { name: '閉じる' }).click()
  await expect(edit).toBeVisible()

  // SC-05-3 画像を1枚外して保存すると「編集済み」。外した画像は S3 からも消える
  await edit.getByRole('button', { name: '画像2を外す' }).click()
  await edit.getByRole('button', { name: '保存する' }).click()
  await expect(edit).toBeHidden()
  await expect(post.getByText('編集した本文')).toBeVisible()
  await expect(post.getByText(/編集済み/)).toBeVisible()
  await expect(post.getByRole('img', { name: /^添付画像/ })).toHaveCount(1)
  const keptImageUrl = await post.getByRole('img', { name: /^添付画像/ }).getAttribute('src')
  await expect.poll(async () => (await page.request.get(removedImageUrl)).status()).toBe(404)

  // SC-05-5 削除すると一覧から消え、画像も S3 から消える
  await post.getByRole('button', { name: 'メニュー' }).click()
  await page.getByRole('menuitem', { name: '削除' }).click()
  const confirm = page.getByRole('alertdialog', { name: 'この投稿を削除しますか？' })
  await expect(confirm.getByText('削除すると元に戻せません。')).toBeVisible()
  await confirm.getByRole('button', { name: '削除する' }).click()
  await expect(page.getByText('編集した本文')).toBeHidden()
  await expect.poll(async () => (await page.request.get(keptImageUrl)).status()).toBe(404)
})

// SC-05-1 他人の投稿には「…」メニューが無い
test('他人の投稿には「…」メニューが出ない', async ({ page }) => {
  const other = await apiAs(newUser('E2E他人'))
  const created = await createPost(other, '他人の投稿')
  await other.dispose()

  await signup(page.request, newUser())
  await page.goto(`/posts/${created.id}`)

  await expect(page.getByText('他人の投稿')).toBeVisible()
  await expect(page.getByRole('button', { name: 'メニュー' })).toHaveCount(0)
})
