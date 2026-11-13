import { expect, test } from '@playwright/test'
import { createPost, newUser, signup } from './support.js'

// テスト仕様書 SC-12 ログイン状態の続き方（F-02）。
// アクセストークン（15分）・リフレッシュトークン（7日）の期限は待てないので、Cookie を消して「切れた」状態を作る

async function signupWithPost(page, body) {
  const user = newUser('E2Eセッション')
  await signup(page.request, user)
  await createPost(page.request, body)
  await page.goto('/')
  const post = page.getByRole('article').filter({ hasText: body })
  await expect(post).toBeVisible()
  return { user, post }
}

// SC-12-1
test('アクセストークンが切れても、裏でトークンを更新して、そのまま操作を続けられる', async ({ page, context }) => {
  const { post } = await signupWithPost(page, 'トークンが切れたあとの投稿')

  await context.clearCookies({ name: 'access_token' })
  // いいねは押した瞬間に見た目が変わる（楽観的更新）ので、トークンの更新と呼び直しが終わるのを、返事で待つ
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/like') && r.request().method() === 'PUT' && r.status() === 200),
    post.getByRole('button', { name: 'いいね 0件' }).click(),
  ])
  expect(response.ok()).toBe(true)
  await expect(post.getByRole('button', { name: 'いいね 1件' })).toBeVisible()
  await expect(page.getByText('いいねできませんでした')).toBeHidden()

  // 新しいアクセストークンを受け取っていて、再読み込みしてもログインしたまま・いいねも残っている
  const names = (await context.cookies()).map((cookie) => cookie.name)
  expect(names).toContain('access_token')
  await page.reload()
  await expect(page.getByRole('article').filter({ hasText: 'トークンが切れたあとの投稿' }).getByRole('button', { name: 'いいね 1件' })).toBeVisible()
})

// SC-12-2
test('リフレッシュトークンも使えなくなったら、次の操作でログイン画面へ移る', async ({ page, context }) => {
  const { post } = await signupWithPost(page, 'ログインし直しの確認')

  await context.clearCookies({ name: 'access_token' })
  await context.clearCookies({ name: 'refresh_token' })
  await post.getByRole('button', { name: 'いいね 0件' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
})

// SC-12-3
test('別のタブでログアウトすると、元のタブも次の操作でログイン画面へ移る', async ({ page, context }) => {
  const { post } = await signupWithPost(page, '別のタブでログアウト')

  const other = await context.newPage()
  await other.goto('/')
  await other.getByRole('button', { name: 'ログアウト' }).click()
  await expect(other).toHaveURL(/\/login$/)
  await other.close()

  await post.getByRole('button', { name: 'いいね 0件' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

// SC-12-4
test('ログアウトしたあとにブラウザの「戻る」を押しても、ログインが必要な画面は見えない', async ({ page }) => {
  const { user } = await signupWithPost(page, '戻るボタンの確認')

  await page.getByRole('link', { name: 'マイページ' }).click()
  await expect(page).toHaveURL(new RegExp(`/users/${user.username}$`))
  await page.getByRole('button', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.goBack()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('region', { name: 'プロフィール' })).toBeHidden()
})
