import { act, cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { checkNewPosts, createPost, deletePost, fetchTimeline } from '../api/posts.js'
import App from '../App.jsx'
import { installIntersectionObserverMock } from '../test/intersectionObserver.js'
import { renderWithAuth } from '../test/renderWithAuth.jsx'
import { NEW_POSTS_CHECK_INTERVAL_MS } from './TimelinePage.jsx'

vi.mock('../api/posts.js', () => ({
  fetchTimeline: vi.fn(),
  checkNewPosts: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
}))

const taro = { username: 'taro_dev', name: '山田太郎' }

function post(id, overrides = {}) {
  return {
    id,
    body: `投稿${id}`,
    createdAt: new Date().toISOString(),
    editedAt: null,
    author: { username: 'taro_dev', name: '山田太郎' },
    images: [],
    isMine: true,
    ...overrides,
  }
}

function bodies() {
  return screen.getAllByRole('article').map((a) => within(a).getByText(/投稿\d+|新しい投稿/).textContent)
}

describe('TimelinePage', () => {
  let io

  beforeEach(() => {
    vi.clearAllMocks()
    io = installIntersectionObserverMock()
    vi.mocked(checkNewPosts).mockResolvedValue({ hasNew: false })
    window.scrollTo = vi.fn()
  })

  afterEach(() => {
    // 先に画面を片付けてから IntersectionObserver の置き換えを戻す（逆だと、片付け前に動いた effect が落ちる）
    cleanup()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  function renderTimeline(userOptions) {
    return { user: userEvent.setup(userOptions), ...renderWithAuth(<App />, { auth: { user: taro }, route: '/' }) }
  }

  // ---------- 表示 ----------

  it('投稿を新しい順に表示する。「もっと見る」ボタンは無い', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(3), post(2)], nextCursor: null })
    renderTimeline()

    await screen.findByText('投稿3')
    expect(bodies()).toEqual(['投稿3', '投稿2'])
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument()
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
  })

  it('読み込みに失敗したら「再読み込み」で読み直せる', async () => {
    vi.mocked(fetchTimeline)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })
    const { user } = renderTimeline()

    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('投稿1')).toBeInTheDocument()
  })

  // ---------- 無限スクロール ----------

  it('一番下までスクロールすると、続きを自動で読み込んで末尾に足す', async () => {
    vi.mocked(fetchTimeline)
      .mockResolvedValueOnce({ items: [post(3), post(2)], nextCursor: 2 })
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })
    renderTimeline()
    await screen.findByText('投稿3')

    act(() => io.scrollToBottom())

    expect(await screen.findByText('投稿1')).toBeInTheDocument()
    expect(fetchTimeline).toHaveBeenLastCalledWith(2)
    expect(bodies()).toEqual(['投稿3', '投稿2', '投稿1'])
    // 最後まで読み込んだら、それ以上は見張らない
    expect(io.observedCount()).toBe(0)
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
  })

  it('続きが無いときは、スクロールしても読み込まない', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(1)], nextCursor: null })
    renderTimeline()
    await screen.findByText('投稿1')

    act(() => io.scrollToBottom())

    expect(fetchTimeline).toHaveBeenCalledTimes(1)
  })

  it('続きの読み込みに失敗したら、自動では読み直さず「再読み込み」ボタンを出す', async () => {
    vi.mocked(fetchTimeline)
      .mockResolvedValueOnce({ items: [post(3)], nextCursor: 3 })
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ items: [post(2)], nextCursor: null })
    const { user } = renderTimeline()
    await screen.findByText('投稿3')

    act(() => io.scrollToBottom())
    const retry = await screen.findByRole('button', { name: '再読み込み' })
    act(() => io.scrollToBottom())
    expect(fetchTimeline).toHaveBeenCalledTimes(2)

    await user.click(retry)
    expect(await screen.findByText('投稿2')).toBeInTheDocument()
  })

  // ---------- 新しい投稿のお知らせ ----------

  it('30秒ごとに、表示中の一番新しい投稿より新しいものがあるか確かめ、あればボタンを出す（件数は出さない）', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(5), post(4)], nextCursor: null })
    vi.mocked(checkNewPosts).mockResolvedValueOnce({ hasNew: false }).mockResolvedValueOnce({ hasNew: true })
    renderTimeline({ advanceTimers: vi.advanceTimersByTime })
    await screen.findByText('投稿5')

    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS))
    expect(checkNewPosts).toHaveBeenCalledWith(5)
    expect(screen.queryByRole('button', { name: /新しい投稿があります/ })).not.toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS))
    expect(screen.getByRole('button', { name: '↑ 新しい投稿があります' })).toBeInTheDocument()

    // お知らせを出したあとは、押されるまで確かめない
    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS * 3))
    expect(checkNewPosts).toHaveBeenCalledTimes(2)
  })

  it('タブが裏にある間は確かめず、表に戻ったらすぐ確かめる', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(5)], nextCursor: null })
    let visibility = 'hidden'
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
    renderTimeline({ advanceTimers: vi.advanceTimersByTime })
    await screen.findByText('投稿5')

    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS * 2))
    expect(checkNewPosts).not.toHaveBeenCalled()

    visibility = 'visible'
    await act(async () => document.dispatchEvent(new Event('visibilitychange')))
    expect(checkNewPosts).toHaveBeenCalledWith(5)
  })

  it('ボタンを押すと、新しい投稿を先頭に足して一番上に戻る（読み込み済みの投稿は残す）', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(fetchTimeline)
      .mockResolvedValueOnce({ items: [post(5), post(4)], nextCursor: 4 })
      .mockResolvedValueOnce({ items: [post(7), post(6), post(5)], nextCursor: 5 })
    vi.mocked(checkNewPosts).mockResolvedValue({ hasNew: true })
    const { user } = renderTimeline({ advanceTimers: vi.advanceTimersByTime })
    await screen.findByText('投稿5')
    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS))

    await user.click(screen.getByRole('button', { name: '↑ 新しい投稿があります' }))

    await screen.findByText('投稿7')
    expect(bodies()).toEqual(['投稿7', '投稿6', '投稿5', '投稿4'])
    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
    expect(screen.queryByRole('button', { name: /新しい投稿があります/ })).not.toBeInTheDocument()
  })

  it('新しい投稿が多すぎて今の一覧とつながらないときは、最新の20件から表示し直す', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.mocked(fetchTimeline)
      .mockResolvedValueOnce({ items: [post(5)], nextCursor: null })
      .mockResolvedValueOnce({ items: [post(30), post(29)], nextCursor: 29 })
    vi.mocked(checkNewPosts).mockResolvedValue({ hasNew: true })
    const { user } = renderTimeline({ advanceTimers: vi.advanceTimersByTime })
    await screen.findByText('投稿5')
    await act(() => vi.advanceTimersByTimeAsync(NEW_POSTS_CHECK_INTERVAL_MS))

    await user.click(screen.getByRole('button', { name: '↑ 新しい投稿があります' }))

    await screen.findByText('投稿30')
    expect(bodies()).toEqual(['投稿30', '投稿29'])
    // 続き（投稿28 以前）はスクロールで読み込める
    expect(screen.queryByText('これ以上の投稿はありません')).not.toBeInTheDocument()
  })

  // ---------- 投稿・削除 ----------

  it('投稿すると、モーダルが閉じて先頭に出る（読み込み直さない）', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(1)], nextCursor: null })
    vi.mocked(createPost).mockResolvedValue(post(2, { body: '新しい投稿' }))
    const { user } = renderTimeline()
    await screen.findByText('投稿1')

    await user.click(screen.getByRole('button', { name: 'いまどうしてる？' }))
    await user.type(screen.getByLabelText('本文'), '新しい投稿')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(bodies()).toEqual(['新しい投稿', '投稿1'])
    expect(fetchTimeline).toHaveBeenCalledTimes(1)
  })

  it('自分の投稿を「…」→「削除」→ 確認して削除すると、一覧から消える', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(2), post(1)], nextCursor: null })
    vi.mocked(deletePost).mockResolvedValue()
    const { user } = renderTimeline()

    const target = (await screen.findByText('投稿2')).closest('article')
    await user.click(within(target).getByRole('button', { name: 'メニュー' }))
    await user.click(within(target).getByRole('menuitem', { name: '削除' }))
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(deletePost).toHaveBeenCalledWith(2)
    expect(bodies()).toEqual(['投稿1'])
  })

  it('他人の投稿には「…」メニューが出ない', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({
      items: [post(1, { isMine: false, author: { username: 'hanako', name: '佐藤花子' } })],
      nextCursor: null,
    })
    renderTimeline()

    await screen.findByText('佐藤花子')
    expect(screen.queryByRole('button', { name: 'メニュー' })).not.toBeInTheDocument()
  })
})
