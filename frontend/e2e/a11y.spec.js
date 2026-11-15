import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { createComment, createPost, newUser, signup } from './support.js'

// テスト仕様書 SC-15 アクセシビリティの自動チェック。
// axe-core で、WCAG 2.1 の A・AA の決まりに反するところ（ラベルの無い入力欄、名前の無いボタン、見出しの順番など）を探す。
// 色のコントラストは appearance.spec.js でライト・ダークの両方を確かめている。
// axe は「機械で判定できるもの」だけを調べる。キーボード操作や読み上げの順番は、ほかの E2E と手動のシナリオで確かめる

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

async function expectNoViolations(page, name) {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG).analyze()
  // 失敗したときに何が悪いか分かるよう、決まりの名前・内容・場所を並べる
  const summary = violations.map((v) => ({
    rule: v.id,
    impact: v.impact,
    help: v.help,
    targets: v.nodes.map((node) => node.target.join(' ')).slice(0, 5),
  }))
  expect(summary, `${name} の画面`).toEqual([])
}

test('ログイン・新規登録の画面', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
  await expectNoViolations(page, 'ログイン')

  await page.getByRole('link', { name: '新規登録' }).click()
  await expect(page.getByRole('button', { name: '登録する' })).toBeVisible()
  await expectNoViolations(page, '新規登録')
})

test('ログイン後の主な画面と、モーダル', async ({ page }) => {
  const user = newUser('E2Eアクセシビリティ')
  await signup(page.request, user)
  const post = await createPost(page.request, 'アクセシビリティ確認の投稿')
  await createComment(page.request, post.id, 'アクセシビリティ確認のコメント')

  await page.goto('/')
  await expect(page.getByRole('article').first()).toBeVisible()
  await expectNoViolations(page, 'ホーム')

  await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
  await expect(page.getByRole('dialog', { name: '新しい投稿' })).toBeVisible()
  await expectNoViolations(page, '新しい投稿のモーダル')
  await page.keyboard.press('Escape')

  await page.goto(`/posts/${post.id}`)
  await expect(page.getByText('アクセシビリティ確認のコメント')).toBeVisible()
  await expectNoViolations(page, '投稿の詳細')

  await page.goto(`/search?q=${user.username}`)
  await expect(page.getByRole('list', { name: '検索結果' }).getByRole('listitem')).toHaveCount(1)
  await expectNoViolations(page, '検索')

  await page.goto(`/users/${user.username}`)
  await expect(page.getByRole('region', { name: 'プロフィール' })).toBeVisible()
  await expectNoViolations(page, 'ユーザーページ')

  await page.getByRole('button', { name: 'プロフィールを編集' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expectNoViolations(page, 'プロフィール編集のモーダル')
})
