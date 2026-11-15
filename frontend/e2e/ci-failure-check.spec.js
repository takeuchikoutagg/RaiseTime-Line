import { expect, test } from '@playwright/test'

// CI が失敗を検知できるかを確かめるための、一時的なテスト（確かめたら消す）
const unusedForCiCheck = 1

test('CI の失敗の確認（わざと失敗させる）', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'この見出しは存在しない' })).toBeVisible({ timeout: 2000 })
})
