import { expect, test } from '@playwright/test'
import { apiAs, chooseFiles, createPost, newUser, PASSWORD, png, signup, tabKey } from './support.js'

// テスト仕様書 SC-07 プロフィール編集（F-12）
test('プロフィール編集：入力の誤り・@ID の重複 → 表示名・自己紹介・画像・@ID を変える → ログインし直しても新しい @ID', async ({
  page,
  browserName,
}) => {
  const taken = newUser('E2E使用済み')
  await (await apiAs(taken)).dispose()
  const user = newUser('E2E編集前')
  await signup(page.request, user)
  await createPost(page.request, '自分の投稿')
  await page.goto(`/users/${user.username}`)

  // SC-07-1 キーボードで開くと、名前の欄にフォーカスが移る
  await page.getByRole('button', { name: 'プロフィールを編集' }).focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog', { name: 'プロフィールを編集' })
  const name = dialog.getByLabel(/^名前/)
  await expect(name).toBeFocused()
  const save = dialog.getByRole('button', { name: '保存する' })

  // SC-07-2 自己紹介が161文字なら保存しない
  const bio = dialog.getByLabel('自己紹介')
  await bio.fill('あ'.repeat(161))
  await save.click()
  await expect(dialog.getByText('自己紹介は160文字以内で入力してください')).toBeVisible()

  // SC-07-3 ほかの人の @ID は使えない
  await bio.fill('E2E の自己紹介です。\n2行目')
  const username = dialog.getByLabel(/^@ユーザーID/)
  await username.fill(taken.username)
  await save.click()
  await expect(dialog.getByText('この@ユーザーIDはすでに使われています')).toBeVisible()

  // SC-07-4・5・7 表示名・画像（キーボードで「画像を変更」）・@ID を変えて保存する
  const newUsername = `${user.username}x`
  await username.fill(newUsername)
  await name.fill('E2E編集後')
  await name.focus()
  await page.keyboard.press(tabKey(browserName, { shift: true }))
  await expect(dialog.getByRole('button', { name: '画像を変更' })).toBeFocused()
  await chooseFiles(page, () => page.keyboard.press('Enter'), [png('avatar.png', [240, 180, 60], 300, 300)])
  await expect(dialog.getByRole('button', { name: '画像を外す' })).toBeVisible()
  await save.click()
  await expect(dialog).toBeHidden()

  await expect(page).toHaveURL(new RegExp(`/users/${newUsername}$`))
  await expect(page.getByRole('heading', { name: 'E2E編集後', level: 1 })).toBeVisible()
  const profile = page.getByRole('region', { name: 'プロフィール' })
  await expect(profile.getByText('E2E の自己紹介です。\n2行目')).toBeVisible()
  await expect(profile.locator('img')).toBeVisible()
  const post = page.getByRole('article').filter({ hasText: '自分の投稿' })
  await expect(post.getByRole('link', { name: 'E2E編集後', exact: true })).toBeVisible()
  await expect(post.locator('img')).toBeVisible()
  await expect(page.getByRole('link', { name: 'マイページ' })).toHaveAttribute('href', `/users/${newUsername}`)

  // ホームの投稿欄のアイコンも新しい画像になる
  await page.getByRole('link', { name: 'ホーム' }).click()
  await expect(page.locator('main').locator('img').first()).toBeVisible()

  // SC-07-6 ログアウトしてログインし直しても、新しい @ID のまま
  await page.getByRole('button', { name: 'ログアウト' }).click()
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill(PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('link', { name: 'マイページ' })).toHaveAttribute('href', `/users/${newUsername}`)

  // 後片付け：プロフィール画像を外して、S3 にテスト用の画像を残さない
  await page.getByRole('link', { name: 'マイページ' }).click()
  await page.getByRole('button', { name: 'プロフィールを編集' }).click()
  await dialog.getByRole('button', { name: '画像を外す' }).click()
  await save.click()
  await expect(dialog).toBeHidden()
  await expect(profile.locator('img')).toHaveCount(0)
})
