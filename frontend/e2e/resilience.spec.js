import { expect, test } from '@playwright/test'
import { apiAs, createPost, deletePost, follow, newUser, signup } from './support.js'

// テスト仕様書 SC-13 通信の失敗・二重送信・ほかの人の操作との行き違い。
// 通信の失敗は、Playwright で API へのリクエストを止めたり、エラーを返させたりして作る

// SC-13-1
test('いいねの通信に失敗したら、見た目を元に戻してメッセージを出す。通信が戻れば押せる', async ({ page }) => {
  await signup(page.request, newUser('E2E通信'))
  await createPost(page.request, '通信が切れたときのいいね')
  await page.goto('/')
  const post = page.getByRole('article').filter({ hasText: '通信が切れたときのいいね' })

  await page.route('**/api/posts/*/like', (route) => route.abort('internetdisconnected'))
  await post.getByRole('button', { name: 'いいね 0件' }).click()
  await expect(post.getByRole('alert')).toHaveText('いいねできませんでした')
  await expect(post.getByRole('button', { name: 'いいね 0件' })).toHaveAttribute('aria-pressed', 'false')

  await page.unroute('**/api/posts/*/like')
  await post.getByRole('button', { name: 'いいね 0件' }).click()
  await expect(post.getByRole('button', { name: 'いいね 1件' })).toHaveAttribute('aria-pressed', 'true')
})

// SC-13-2
test('投稿がサーバーのエラーで失敗したら、モーダルを開いたまま入力を残し、メッセージを出す', async ({ page }) => {
  await signup(page.request, newUser('E2E通信'))
  await page.goto('/')

  await page.route('**/api/posts', (route) =>
    route.request().method() === 'POST'
      ? route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'INTERNAL_ERROR', message: '予期しないエラーが発生しました', errors: [] }),
        })
      : route.fallback(),
  )
  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  const compose = page.getByRole('dialog', { name: '新しい投稿' })
  await compose.getByLabel('本文').fill('失敗しても消えない本文')
  await compose.getByRole('button', { name: '投稿する' }).click()

  await expect(compose.getByText('予期しないエラーが発生しました')).toBeVisible()
  await expect(compose.getByLabel('本文')).toHaveValue('失敗しても消えない本文')

  // 直ったら、そのまま投稿できる
  await page.unroute('**/api/posts')
  await compose.getByRole('button', { name: '投稿する' }).click()
  await expect(compose).toBeHidden()
  await expect(page.getByRole('article').filter({ hasText: '失敗しても消えない本文' })).toHaveCount(1)
})

// SC-13-3
test('「投稿する」を素早く2回押しても、投稿は1件だけ', async ({ page }) => {
  const user = newUser('E2E二重送信')
  await signup(page.request, user)
  await page.goto('/')

  // サーバーの返事を遅らせて、送信中にもう一度押せる時間を作る
  await page.route('**/api/posts', async (route) => {
    if (route.request().method() === 'POST') await new Promise((resolve) => setTimeout(resolve, 1000))
    await route.fallback()
  })
  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  const compose = page.getByRole('dialog', { name: '新しい投稿' })
  await compose.getByLabel('本文').fill('二重送信の確認')
  await compose.getByRole('button', { name: '投稿する' }).dblclick()
  await expect(compose).toBeHidden()

  const response = await page.request.get(`/api/users/${user.username}/posts`)
  const { items } = await response.json()
  expect(items.filter((item) => item.body === '二重送信の確認')).toHaveLength(1)
})

// SC-13-4・SC-13-5
test('ほかの人が消した投稿に、いいね・コメントしようとすると「投稿が見つかりません」と出る', async ({ page }) => {
  const bob = newUser('E2E行き違い')
  const bobApi = await apiAs(bob)
  const gone = await createPost(bobApi, 'あとで消える投稿')
  const gone2 = await createPost(bobApi, 'あとで消える投稿その2')
  await signup(page.request, newUser())
  await follow(page.request, bob.username)

  // タイムラインで見ている間に消された → いいね
  await page.goto('/')
  const post = page.getByRole('article').filter({ hasText: 'あとで消える投稿その2' })
  await expect(post).toBeVisible()
  await deletePost(bobApi, gone2.id)
  await post.getByRole('button', { name: 'いいね 0件' }).click()
  await expect(post.getByRole('alert')).toHaveText('投稿が見つかりません')
  await expect(post.getByRole('button', { name: 'いいね 0件' })).toBeVisible()

  // 詳細を開いている間に消された → コメント
  await page.goto(`/posts/${gone.id}`)
  await expect(page.getByRole('main').getByText('あとで消える投稿')).toBeVisible()
  await deletePost(bobApi, gone.id)
  await bobApi.dispose()
  await page.getByRole('textbox', { name: 'コメント' }).fill('届かないコメント')
  await page.getByRole('button', { name: '送信' }).click()
  await expect(page.getByText('投稿が見つかりません')).toBeVisible()

  // 開き直すと、見つからない画面になる
  await page.reload()
  await expect(page.getByText('投稿が見つかりません')).toBeVisible()
  await expect(page.getByRole('link', { name: 'タイムラインへ戻る' })).toBeVisible()
})
