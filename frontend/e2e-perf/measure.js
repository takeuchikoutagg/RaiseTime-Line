// ブラウザのパフォーマンステストの部品（docs/パフォーマンステスト.md 10章）

export const PASSWORD = 'password123'
// perf/seed/seed.sql で作ったユーザー。100人をフォローしていて、タイムラインに投稿が十分にある
export const PERF_USER = 'perf_00001'
// 検索に使う言葉（seed.sql の名前に含まれ、2000人が当たる）
export const SEARCH_WORD = 'taro'

// 1つの測定を何回くり返して、中央値を取るか（PERF_RUNS=5 npm run e2e:perf のように変えられる）
export const RUNS = Number(process.env.PERF_RUNS || 3)

// 測る条件。スマホ相当は、Lighthouse のモバイルの測定と同じ値（RTT 150ms・下り 1.6Mbps・上り 750Kbps・CPU 4倍遅い）
export const PROFILES = {
  pc: {
    label: 'PC（制限なし）',
    context: { viewport: { width: 1280, height: 800 } },
    throttle: null,
  },
  mobile: {
    label: 'スマホ相当（4G・CPU 4倍遅い）',
    context: { viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true },
    throttle: { latency: 150, download: (1.6 * 1024 * 1024) / 8, upload: (750 * 1024) / 8, cpu: 4 },
  },
}

// 合格ライン（ms。CLS は数、大きさは KB）。Google の Core Web Vitals で「良好」とされる値をもとにした（根拠は docs の 10.2）。
// どの値も、くり返した結果の中央値で判定する
export const BUDGETS = {
  ttfb: 800,
  fcp: 1800,
  lcp: 2500,
  cls: 0.1,
  firstPost: 2500,
  loginToTimeline: 2500,
  inp: 200,
  composeOpen: 200,
  postDetail: 1500,
  nextPage: 1500,
  search: 1000,
  jsKB: 400,
  cssKB: 60,
}

export const LABELS = {
  ttfb: 'TTFB（最初の返事まで）',
  fcp: 'FCP（最初に何か出るまで）',
  lcp: 'LCP（一番大きな部分が出るまで）',
  cls: 'CLS（画面のずれ）',
  firstPost: 'タイムラインの最初の投稿が見えるまで',
  loginToTimeline: '「ログイン」を押してからタイムラインが出るまで',
  inp: 'INP（押してから次の描画まで。いいね・投稿モーダル）',
  likeVisible: 'いいねを押してから ♥ に変わるまで',
  composeOpen: '「投稿」を押してから入力できるまで',
  postDetail: '投稿を押してから本文とコメントが出るまで',
  nextPage: '一番下まで行ってから次の20件が出るまで',
  search: '打ち終わってから検索結果が出るまで（待ち時間300msを含む）',
  jsKB: '最初に読み込む JS（KB）',
  cssKB: '最初に読み込む CSS（KB）',
}

export function median(values) {
  const sorted = values.filter((v) => typeof v === 'number' && !Number.isNaN(v)).sort((a, b) => a - b)
  if (sorted.length === 0) return null
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

// 通信と CPU の速さを制限する（Chromium の CDP）。ページごとにかける
export async function applyThrottle(page, throttle) {
  if (!throttle) return
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Network.enable')
  await cdp.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: throttle.latency,
    downloadThroughput: throttle.download,
    uploadThroughput: throttle.upload,
  })
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: throttle.cpu })
}

// ページの JavaScript より先に動かし、ブラウザが記録する性能の情報を集める
// - LCP・CLS・操作の反応（Event Timing。INP のもとになる値）を PerformanceObserver で受け取る
// - 利用者が最後に押した・打った時刻（lastInputAt）を記録する。そこから画面が変わるまでの時間を測る
export function observerScript() {
  const perf = { lcp: 0, cls: 0, events: [], lastInputAt: 0 }
  window.__perf = perf
  const observe = (type, onEntry, options = {}) => {
    try {
      new PerformanceObserver((list) => list.getEntries().forEach(onEntry)).observe({ type, buffered: true, ...options })
    } catch {
      // その種類の記録に対応していないブラウザでは何もしない
    }
  }
  observe('largest-contentful-paint', (entry) => {
    perf.lcp = entry.startTime
  })
  observe('layout-shift', (entry) => {
    if (!entry.hadRecentInput) perf.cls += entry.value
  })
  observe(
    'event',
    (entry) => {
      if (entry.interactionId) perf.events.push({ name: entry.name, duration: entry.duration, interactionId: entry.interactionId })
    },
    { durationThreshold: 16 },
  )
  for (const type of ['pointerdown', 'keydown']) {
    document.addEventListener(
      type,
      (event) => {
        if (event.isTrusted) perf.lastInputAt = event.timeStamp
      },
      { capture: true },
    )
  }
}

// 読み込みが落ち着くまで待つ：load のあと、読み込み終わった通信の数が 500ms 増えなくなるまで。
// Playwright の networkidle は使わない。ログインしていないときのトークン更新（/api/auth/refresh の 401）は、
// 画面が返事の本文を読まないので、ブラウザからは「終わっていない通信」に見え、networkidle にならないため
export async function settle(page) {
  await page.waitForLoadState('load')
  await page.waitForFunction(
    () => {
      const count = performance.getEntriesByType('resource').length
      const now = performance.now()
      if (window.__settle?.count !== count) window.__settle = { count, since: now }
      return now - window.__settle.since >= 500
    },
    null,
    { polling: 100, timeout: 60_000 },
  )
}

// ページを開いたときの値（TTFB・FCP・LCP・CLS・読み込んだ JS と CSS の大きさ）
export async function pageLoadMetrics(page) {
  // LCP は、読み込みが落ち着くまで更新されうるので、落ち着いてから読む
  await settle(page)
  return page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0]
    const fcp = performance.getEntriesByName('first-contentful-paint')[0]
    const resources = performance.getEntriesByType('resource')
    const bytes = (extension) =>
      resources
        .filter((r) => new URL(r.name).pathname.endsWith(extension))
        .reduce((sum, r) => sum + (r.decodedBodySize || 0), 0)
    return {
      ttfb: nav ? nav.responseStart : null,
      fcp: fcp ? fcp.startTime : null,
      lcp: window.__perf.lcp || null,
      cls: window.__perf.cls,
      jsKB: bytes('.js') / 1024,
      cssKB: bytes('.css') / 1024,
    }
  })
}

// 最後に押した・打った時刻から、条件を満たした（画面が変わった）最初の描画までの時間（ms）。
// 条件は、描画のたび（requestAnimationFrame）に確かめる
export async function elapsedSinceInput(page, predicate, arg) {
  const handle = await page.waitForFunction(
    ({ source, value }) => {
      const check = new Function('value', `return (${source})(value)`)
      return check(value) ? performance.now() - window.__perf.lastInputAt : false
    },
    { source: predicate.toString(), value: arg },
    { polling: 'raf', timeout: 60_000 },
  )
  return handle.jsonValue()
}

// ブラウザが記録した操作の反応時間のうち、いちばん長いもの（INP の考え方）。読んだら記録を空にする
export async function takeInp(page) {
  return page.evaluate(() => {
    const byInteraction = new Map()
    for (const event of window.__perf.events) {
      byInteraction.set(event.interactionId, Math.max(byInteraction.get(event.interactionId) || 0, event.duration))
    }
    window.__perf.events = []
    return byInteraction.size ? Math.max(...byInteraction.values()) : null
  })
}

// ログインした状態（Cookie）を作る。測るたびに新しいブラウザの状態（キャッシュなし）で始めるため、Cookie だけを使い回す
export async function loginState(browser, baseURL) {
  const context = await browser.newContext({ baseURL })
  await context.request.get('/api/auth/csrf')
  const csrf = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await context.request.post('/api/auth/login', {
    data: { email: `${PERF_USER}@example.com`, password: PASSWORD },
    headers: { 'X-XSRF-TOKEN': csrf },
  })
  if (!response.ok()) throw new Error(`${PERF_USER} でログインできません（${response.status()}）`)
  const state = await context.storageState()
  await context.close()
  return state
}

// 測るためのページを開く準備：新しいブラウザの状態・性能の記録・速さの制限
export async function newMeasuredPage(browser, profile, { baseURL, storageState } = {}) {
  const context = await browser.newContext({ ...profile.context, baseURL, storageState, locale: 'ja-JP' })
  await context.addInitScript(observerScript)
  const page = await context.newPage()
  await applyThrottle(page, profile.throttle)
  return { context, page }
}

// 結果を表にする（ターミナルに出す）
export function formatTable(profileLabel, results) {
  const rows = Object.entries(results).map(([key, { median: value, runs }]) => {
    // 「login.fcp」のような名前は、ログイン画面で測った FCP。合格ラインと名前は FCP のものを使う
    const base = key.split('.').pop()
    const label = key.startsWith('login.') ? `ログイン画面の ${LABELS[base]}` : (LABELS[base] ?? key)
    const budget = BUDGETS[base]
    const format = (v) => (v == null ? '−' : base === 'cls' ? v.toFixed(3) : `${Math.round(v)}`)
    const verdict = budget == null || value == null ? '（参考）' : value <= budget ? 'OK' : 'NG'
    return `| ${label} | ${format(value)} | ${budget ?? '−'} | ${verdict} | ${runs.map(format).join(' / ')} |`
  })
  return [
    `### ${profileLabel}`,
    '',
    '| 測るもの | 中央値 | 合格ライン | 判定 | 各回 |',
    '|---|---|---|---|---|',
    ...rows,
  ].join('\n')
}
