import { expect, test } from '@playwright/test'
import { apiAs, newUser, PASSWORD, signup } from './support.js'

// テスト仕様書 SC-01 アカウント（F-01 新規登録・F-02 ログイン・ログアウト）
test('新規登録 → ログアウト → ログインの失敗 → ログイン → 再読み込みしてもログインしたまま', async ({ page }) => {
  const user = newUser('E2Eアカウント')

  // SC-01-1 ログインしていないとログイン画面へ
  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)

  // SC-01-2 確認用パスワードが違うと登録しない
  await page.getByRole('link', { name: '新規登録' }).click()
  await page.getByLabel(/^名前/).fill(user.name)
  await page.getByLabel(/^@ユーザーID/).fill(user.username)
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel(/^パスワード （/).fill(PASSWORD)
  await page.getByLabel('パスワード（確認）').fill('password999')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByText('パスワード（確認）がパスワードと一致しません')).toBeVisible()
  await expect(page).toHaveURL(/\/signup$/)

  // SC-01-4 正しく登録するとホームへ。タイムラインは空で、検索へ案内する
  await page.getByLabel('パスワード（確認）').fill(PASSWORD)
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('heading', { name: 'ホーム', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'ユーザーを探す' })).toBeVisible()

  // SC-01-5 ログアウト
  await page.getByRole('button', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL(/\/login$/)

  // SC-01-6 パスワードが違う
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill('wrongpass1')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('alert')).toHaveText('メールアドレスまたはパスワードが正しくありません')

  // SC-01-7 メールアドレスの大文字・小文字は区別しない
  await page.getByLabel('メールアドレス').fill(user.email.toUpperCase())
  await page.getByLabel('パスワード').fill(PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('link', { name: 'マイページ' })).toHaveAttribute('href', `/users/${user.username}`)

  // SC-01-8 再読み込みしてもログインしたまま
  await page.reload()
  await expect(page.getByRole('link', { name: 'マイページ' })).toHaveAttribute('href', `/users/${user.username}`)
})

// SC-01-3 登録済みのメールアドレス
test('登録済みのメールアドレスでは登録できない', async ({ page }) => {
  const existing = newUser()
  await (await apiAs(existing)).dispose()

  await page.goto('/signup')
  await page.getByLabel(/^名前/).fill('重複テスト')
  await page.getByLabel(/^@ユーザーID/).fill(newUser().username)
  await page.getByLabel('メールアドレス').fill(existing.email)
  await page.getByLabel(/^パスワード （/).fill(PASSWORD)
  await page.getByLabel('パスワード（確認）').fill(PASSWORD)
  await page.getByRole('button', { name: '登録する' }).click()

  await expect(page.getByText('このメールアドレスはすでに登録されています')).toBeVisible()
})

// テスト仕様書 SC-09 画面の移動・存在しないもの
test('ログインしていないと、どの URL もログイン画面へ', async ({ page }) => {
  await page.goto('/users/someone')
  await expect(page).toHaveURL(/\/login$/)
})

test('存在しない URL・投稿・ユーザー', async ({ page }) => {
  await signup(page.request, newUser())

  await page.goto('/no-such-page')
  await expect(page).toHaveURL(/\/$/)

  await page.goto('/posts/999999999')
  await expect(page.getByText('投稿が見つかりません')).toBeVisible()
  await expect(page.getByRole('link', { name: 'タイムラインへ戻る' })).toBeVisible()

  await page.goto('/users/nobody_xyz')
  await expect(page.getByText('ユーザーが見つかりません')).toBeVisible()
})
