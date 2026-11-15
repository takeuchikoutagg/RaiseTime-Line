import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import {
  BUDGETS,
  LABELS,
  PASSWORD,
  PERF_USER,
  PROFILES,
  RUNS,
  SEARCH_WORD,
  elapsedSinceInput,
  formatTable,
  loginState,
  median,
  newMeasuredPage,
  pageLoadMetrics,
  settle,
  takeInp,
} from './measure.js'

// ブラウザのパフォーマンステスト（docs/パフォーマンステスト.md 10章）。
// それぞれの測定を RUNS 回くり返して中央値を取り、合格ライン（measure.js の BUDGETS）と比べる。
// 合格ラインを超えたものは失敗にするが、ほかの測定は続ける（expect.soft）。結果は perf/results/ に JSON で残す

const RESULTS_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'perf', 'results')

test.describe.configure({ mode: 'serial' })

let profile
let baseURL
let storageState
const results = {}

function record(key, runs) {
  results[key] = { median: median(runs), runs }
}

function checkBudgets(keys) {
  for (const key of keys) {
    const { median: value } = results[key] ?? {}
    expect.soft(value, `${LABELS[key]} の中央値`).not.toBeNull()
    if (value != null && BUDGETS[key] != null) {
      expect.soft(value, `${LABELS[key]}（合格ライン ${BUDGETS[key]}）`).toBeLessThanOrEqual(BUDGETS[key])
    }
  }
}

test.beforeAll(async ({ browser }, testInfo) => {
  profile = PROFILES[testInfo.project.name]
  baseURL = testInfo.project.use.baseURL
  storageState = await loginState(browser, baseURL)
})

test.afterAll(async () => {
  const testInfo = test.info()
  const table = formatTable(profile.label, results)
  console.log(`\n${table}\n`)
  mkdirSync(RESULTS_DIR, { recursive: true })
  const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\..+/, '').replace('T', '-')
  const file = join(RESULTS_DIR, `browser-${testInfo.project.name}-${stamp}.json`)
  writeFileSync(file, JSON.stringify({ profile: profile.label, runs: RUNS, budgets: BUDGETS, results }, null, 2))
  console.log(`結果：${file}`)
})

test('ログイン画面を開く（ログインしていない状態）', async ({ browser }) => {
  const runs = { ttfb: [], fcp: [], lcp: [], cls: [], jsKB: [], cssKB: [] }
  for (let i = 0; i < RUNS; i++) {
    const { context, page } = await newMeasuredPage(browser, profile, { baseURL })
    await page.goto('/login')
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
    const metrics = await pageLoadMetrics(page)
    for (const key of Object.keys(runs)) runs[key].push(metrics[key])
    await context.close()
  }
  // タイムラインを開いたときの値と区別するため、名前を分けて記録する
  record('ttfb', runs.ttfb)
  record('jsKB', runs.jsKB)
  record('cssKB', runs.cssKB)
  results['login.fcp'] = { median: median(runs.fcp), runs: runs.fcp }
  results['login.lcp'] = { median: median(runs.lcp), runs: runs.lcp }
  results['login.cls'] = { median: median(runs.cls), runs: runs.cls }
  checkBudgets(['ttfb', 'jsKB', 'cssKB'])
  for (const key of ['fcp', 'lcp', 'cls']) {
    expect.soft(results[`login.${key}`].median, `ログイン画面の ${LABELS[key]}`).toBeLessThanOrEqual(BUDGETS[key])
  }
})

test('「ログイン」を押してから、タイムラインが出るまで', async ({ browser }) => {
  const runs = []
  for (let i = 0; i < RUNS; i++) {
    const { context, page } = await newMeasuredPage(browser, profile, { baseURL })
    await page.goto('/login')
    await page.getByLabel('メールアドレス').fill(`${PERF_USER}@example.com`)
    await page.getByLabel('パスワード').fill(PASSWORD)
    await page.getByRole('button', { name: 'ログイン' }).click()
    runs.push(await elapsedSinceInput(page, () => document.querySelector('main article') !== null))
    await context.close()
  }
  record('loginToTimeline', runs)
  checkBudgets(['loginToTimeline'])
})

test('タイムラインを開く（ログインした状態）', async ({ browser }) => {
  const runs = { fcp: [], lcp: [], cls: [], firstPost: [] }
  for (let i = 0; i < RUNS; i++) {
    const { context, page } = await newMeasuredPage(browser, profile, { baseURL, storageState })
    await page.goto('/')
    // ページを開き始めてから、最初の投稿が描かれたフレームまで
    const firstPost = await page.waitForFunction(() => document.querySelector('main article') && performance.now(), null, {
      polling: 'raf',
      timeout: 60_000,
    })
    runs.firstPost.push(await firstPost.jsonValue())
    const metrics = await pageLoadMetrics(page)
    runs.fcp.push(metrics.fcp)
    runs.lcp.push(metrics.lcp)
    runs.cls.push(metrics.cls)
    await context.close()
  }
  for (const key of Object.keys(runs)) record(key, runs[key])
  checkBudgets(['fcp', 'lcp', 'cls', 'firstPost'])
})

test('タイムラインでの操作（いいね・投稿モーダル・詳細・続きの読み込み）', async ({ browser }) => {
  const runs = { likeVisible: [], composeOpen: [], postDetail: [], nextPage: [], inp: [] }
  for (let i = 0; i < RUNS; i++) {
    const { context, page } = await newMeasuredPage(browser, profile, { baseURL, storageState })
    await page.goto('/')
    const first = page.getByRole('article').first()
    await expect(first).toBeVisible()
    await settle(page)
    await takeInp(page)

    // いいね：押してから ♥ に変わるまで。もう一度押して元に戻す（データを変えたままにしない）
    const like = first.getByRole('button', { name: /^いいね/ })
    const pressed = await like.getAttribute('aria-pressed')
    const likeDone = page.waitForResponse((r) => r.url().endsWith('/like'))
    await like.click()
    runs.likeVisible.push(
      await elapsedSinceInput(
        page,
        (before) => document.querySelector('main article button[aria-pressed]')?.getAttribute('aria-pressed') !== before,
        pressed,
      ),
    )
    await likeDone
    const undoDone = page.waitForResponse((r) => r.url().endsWith('/like'))
    await like.click()
    await undoDone

    // 投稿モーダル：押してから本文の欄に入力できる（フォーカスが当たる）まで
    await page.getByRole('navigation', { name: 'メイン' }).getByRole('button', { name: '投稿' }).click()
    runs.composeOpen.push(
      await elapsedSinceInput(page, () => document.activeElement?.closest('[role="dialog"]') && document.activeElement.tagName === 'TEXTAREA'),
    )
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()
    runs.inp.push(await takeInp(page))

    // 続きの読み込み：一番下まで行ってから、21件目の投稿が描かれるまで
    await page.evaluate(() => {
      window.__perf.lastInputAt = performance.now()
      window.scrollTo(0, document.body.scrollHeight)
    })
    runs.nextPage.push(await elapsedSinceInput(page, () => document.querySelectorAll('main article').length > 20))

    // 投稿の詳細：本文を押してから、本文とコメント一覧（読み込み中の表示が消えたもの）が出るまで
    await page.evaluate(() => window.scrollTo(0, 0))
    await first.locator('a[href^="/posts/"]').first().click()
    runs.postDetail.push(
      await elapsedSinceInput(
        page,
        () => document.querySelector('section[aria-label="コメント一覧"]') && !document.querySelector('main output'),
      ),
    )
    await context.close()
  }
  for (const key of Object.keys(runs)) record(key, runs[key])
  checkBudgets(['composeOpen', 'postDetail', 'nextPage', 'inp'])
})

test('ユーザー検索：打ち終わってから結果が出るまで', async ({ browser }) => {
  const runs = []
  for (let i = 0; i < RUNS; i++) {
    const { context, page } = await newMeasuredPage(browser, profile, { baseURL, storageState })
    await page.goto('/search')
    const box = page.getByRole('searchbox', { name: 'ユーザー検索' })
    await box.click()
    await page.keyboard.type(SEARCH_WORD)
    runs.push(await elapsedSinceInput(page, () => document.querySelector('ul[aria-label="検索結果"] li') !== null))
    await context.close()
  }
  record('search', runs)
  checkBudgets(['search'])
})
