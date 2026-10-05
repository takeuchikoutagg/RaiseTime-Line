import { expect, test } from '@playwright/test'
import { apiAs, createPost, follow, newUser, signup } from './support.js'

// テスト仕様書 SC-08 新しい投稿のお知らせ（F-03）。30秒ごとの確認は、時計を早送りして待たずに確かめる
test('フォロー中の人が投稿すると、30秒以内に「新しい投稿があります」が出て、押すと先頭に足される', async ({ page }) => {
  const bob = newUser('E2E新着')
  const bobApi = await apiAs(bob)
  await createPost(bobApi, '前からある投稿')
  await signup(page.request, newUser())
  await follow(page.request, bob.username)

  await page.clock.install()
  await page.goto('/')
  await expect(page.getByText('前からある投稿')).toBeVisible()
  const banner = page.getByRole('button', { name: '↑ 新しい投稿があります' })
  await expect(banner).toBeHidden()

  await createPost(bobApi, 'あとから来た投稿')
  await bobApi.dispose()
  await page.clock.fastForward(30_000)

  // 件数は出さない
  await expect(banner).toBeVisible()
  await banner.click()
  await expect(banner).toBeHidden()
  await expect(page.getByRole('article').first()).toContainText('あとから来た投稿')
  await expect(page.getByRole('article').nth(1)).toContainText('前からある投稿')
})

// テスト仕様書 SC-11 主な画面でコンソールにエラーが出ない
test('主な画面を開いても、コンソールにエラーが出ない', async ({ page }) => {
  const errors = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))
  const user = newUser()
  await signup(page.request, user)
  const post = await createPost(page.request, 'コンソール確認用の投稿')

  for (const path of ['/', '/search?q=e2e', `/users/${user.username}`, `/posts/${post.id}`]) {
    await page.goto(path)
    await expect(page.getByRole('main')).toBeVisible()
    await page.waitForLoadState('networkidle')
  }

  expect(errors).toEqual([])
})
