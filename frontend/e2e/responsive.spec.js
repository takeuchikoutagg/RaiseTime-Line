import { expect, test } from '@playwright/test'
import { createPost, newUser, signup } from './support.js'

// テスト仕様書 SC-10 スマホ幅（375×812）
test.use({ viewport: { width: 375, height: 812 } })

async function hasHorizontalScroll(page) {
  return page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
}

test('スマホ幅では、ロゴを隠してヘッダーのボタンが1行に収まり、どの画面も横スクロールが出ない', async ({ page }) => {
  const user = newUser('E2Eスマホ')
  await signup(page.request, user)
  const post = await createPost(page.request, 'スマホ幅で見る投稿です。'.repeat(8))

  await page.goto('/')
  const nav = page.getByRole('navigation', { name: 'メイン' })
  // ホーム・検索・マイページ・投稿・ログアウト（ヘッダーが表示されてから確かめる）
  await expect(nav.locator('a, button')).toHaveCount(5)
  await expect(page.getByRole('link', { name: 'RaiseTime Line' })).toBeHidden()
  const tops = await nav.locator('a, button').evaluateAll((items) => items.map((el) => Math.round(el.getBoundingClientRect().top)))
  expect(new Set(tops).size).toBe(1)

  for (const path of ['/', '/search?q=e2e', `/users/${user.username}`, `/posts/${post.id}`]) {
    await page.goto(path)
    await expect(page.getByRole('main')).toBeVisible()
    // 投稿などを読み込み終えてから確かめる
    await page.waitForLoadState('networkidle')
    expect(await hasHorizontalScroll(page), path).toBe(false)
  }
})

test('スマホ幅でも、投稿とプロフィール編集のモーダルが画面に収まる', async ({ page }) => {
  const user = newUser()
  await signup(page.request, user)

  await page.goto('/')
  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  const compose = await page.getByRole('dialog', { name: '新しい投稿' }).boundingBox()
  expect(compose.x).toBeGreaterThanOrEqual(0)
  expect(compose.x + compose.width).toBeLessThanOrEqual(375)
  await page.keyboard.press('Escape')

  await page.goto(`/users/${user.username}`)
  await page.getByRole('button', { name: 'プロフィールを編集' }).click()
  const profile = await page.getByRole('dialog', { name: 'プロフィールを編集' }).boundingBox()
  expect(profile.x).toBeGreaterThanOrEqual(0)
  expect(profile.x + profile.width).toBeLessThanOrEqual(375)
  expect(await hasHorizontalScroll(page)).toBe(false)
})
