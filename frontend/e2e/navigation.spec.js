import { expect, test } from '@playwright/test'
import { createComment, createPost, newUser, signup } from './support.js'

// テスト仕様書 SC-14 URL を直接開く・再読み込み・戻る／進む（画面の切り替えはブラウザの中の JavaScript が行う SPA のため）

// SC-14-1
test('投稿の詳細とユーザーページは、URL を直接開いても、再読み込みしても同じ画面が出る', async ({ page }) => {
  const user = newUser('E2EURL')
  await signup(page.request, user)
  const post = await createPost(page.request, 'URL を直接開く投稿')
  await createComment(page.request, post.id, 'URL を直接開いたときのコメント')

  await page.goto(`/posts/${post.id}`)
  await expect(page.getByRole('main').getByText('URL を直接開く投稿')).toBeVisible()
  await expect(page.getByRole('region', { name: 'コメント一覧' }).getByText('URL を直接開いたときのコメント')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: 'コメント一覧' }).getByText('URL を直接開いたときのコメント')).toBeVisible()

  await page.goto(`/users/${user.username}`)
  await expect(page.getByRole('region', { name: 'プロフィール' }).getByText(`@${user.username}`)).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: 'プロフィール' }).getByText(`@${user.username}`)).toBeVisible()
})

// SC-14-2
test('ブラウザの「戻る」「進む」で、前の画面に戻り、また進める', async ({ page }) => {
  await signup(page.request, newUser('E2E戻る'))
  const post = await createPost(page.request, '戻る・進むの投稿')

  await page.goto('/')
  await page.getByRole('article').filter({ hasText: '戻る・進むの投稿' }).getByRole('link', { name: 'コメント 0件' }).click()
  await expect(page).toHaveURL(new RegExp(`/posts/${post.id}$`))
  await page.getByRole('link', { name: '検索' }).click()
  await expect(page).toHaveURL(/\/search$/)

  await page.goBack()
  await expect(page).toHaveURL(new RegExp(`/posts/${post.id}$`))
  await expect(page.getByRole('main').getByText('戻る・進むの投稿')).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { name: 'ホーム', level: 1 })).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: '戻る・進むの投稿' })).toBeVisible()
  await page.goForward()
  await expect(page).toHaveURL(new RegExp(`/posts/${post.id}$`))
})

// SC-14-3
test('検索の URL（?q=）を直接開くと、そのキーワードで検索した結果が出る', async ({ page }) => {
  const user = newUser('E2E検索URL')
  await signup(page.request, user)

  await page.goto(`/search?q=${user.username}`)
  await expect(page.getByRole('searchbox', { name: 'ユーザー検索' })).toHaveValue(user.username)
  await expect(page.getByRole('list', { name: '検索結果' }).getByRole('listitem')).toHaveCount(1)
})
