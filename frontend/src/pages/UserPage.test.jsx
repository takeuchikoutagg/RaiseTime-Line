import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.js'
import { checkNewPosts, deletePost, fetchTimeline } from '../api/posts.js'
import { fetchUser, fetchUserPosts, followUser } from '../api/users.js'
import App from '../App.jsx'
import { installIntersectionObserverMock } from '../test/intersectionObserver.js'
import { renderWithAuth } from '../test/renderWithAuth.jsx'

vi.mock('../api/posts.js', () => ({
  fetchTimeline: vi.fn(),
  checkNewPosts: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
  fetchPost: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
  fetchComments: vi.fn(),
  createComment: vi.fn(),
}))

vi.mock('../api/users.js', () => ({
  fetchUser: vi.fn(),
  fetchUserPosts: vi.fn(),
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
}))

const taro = { username: 'taro_dev', name: '山田太郎' }

function profile(overrides = {}) {
  return {
    username: 'hanako',
    name: '佐藤花子',
    followingCount: 8,
    followerCount: 15,
    followedByMe: false,
    isMe: false,
    ...overrides,
  }
}

function post(id, overrides = {}) {
  return {
    id,
    body: `投稿${id}`,
    createdAt: new Date().toISOString(),
    editedAt: null,
    author: { username: 'hanako', name: '佐藤花子' },
    images: [],
    likeCount: 0,
    commentCount: 0,
    likedByMe: false,
    isMine: false,
    ...overrides,
  }
}

function bodies() {
  return screen.getAllByRole('article').map((a) => within(a).getByText(/^投稿\d+$/).textContent)
}

describe('UserPage', () => {
  let io

  beforeEach(() => {
    // 前のテストで使われなかった mockResolvedValueOnce が残らないよう、返す値も消す
    vi.resetAllMocks()
    io = installIntersectionObserverMock()
    vi.mocked(checkNewPosts).mockResolvedValue({ hasNew: false })
    window.scrollTo = vi.fn()
  })

  afterEach(() => {
    // 先に画面を片付けてから IntersectionObserver の置き換えを戻す
    cleanup()
    vi.unstubAllGlobals()
  })

  function renderPage(route = '/users/hanako') {
    return { user: userEvent.setup(), ...renderWithAuth(<App />, { auth: { user: taro }, route }) }
  }

  it('名前・@ユーザーID・フォロー数・フォロワー数・フォローボタン・投稿一覧を表示する', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile())
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [post(2), post(1)], nextCursor: null })
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: '佐藤花子' })).toBeInTheDocument()
    expect(fetchUser).toHaveBeenCalledWith('hanako')
    expect(fetchUserPosts).toHaveBeenCalledWith('hanako')
    const profileSection = screen.getByRole('region', { name: 'プロフィール' })
    expect(within(profileSection).getByRole('heading', { level: 2, name: '佐藤花子' })).toBeInTheDocument()
    expect(within(profileSection).getByText('@hanako')).toBeInTheDocument()
    expect(profileSection).toHaveTextContent('8 フォロー')
    expect(profileSection).toHaveTextContent('15 フォロワー')
    expect(within(profileSection).getByRole('button', { name: 'フォローする' })).toBeInTheDocument()
    expect(bodies()).toEqual(['投稿2', '投稿1'])
  })

  it('フォローすると「フォロー中」になり、フォロワー数が API の値に変わる', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile())
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(followUser).mockResolvedValue({ followedByMe: true, followerCount: 16 })
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'フォローする' }))

    expect(followUser).toHaveBeenCalledWith('hanako')
    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'プロフィール' })).toHaveTextContent('16 フォロワー')
  })

  it('自分のページにはフォローボタンを出さない。自分の投稿には「…」メニューが出る', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile({ username: 'taro_dev', name: '山田太郎', isMe: true }))
    vi.mocked(fetchUserPosts).mockResolvedValue({
      items: [post(1, { author: taro, isMine: true })],
      nextCursor: null,
    })
    renderPage('/users/taro_dev')

    await screen.findByText('投稿1')
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'メニュー' })).toBeInTheDocument()
  })

  it('自分の投稿を削除すると、一覧から消える', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile({ username: 'taro_dev', name: '山田太郎', isMe: true }))
    vi.mocked(fetchUserPosts).mockResolvedValue({
      items: [post(2, { author: taro, isMine: true }), post(1, { author: taro, isMine: true })],
      nextCursor: null,
    })
    vi.mocked(deletePost).mockResolvedValue()
    const { user } = renderPage('/users/taro_dev')

    const target = (await screen.findByText('投稿2')).closest('article')
    await user.click(within(target).getByRole('button', { name: 'メニュー' }))
    await user.click(within(target).getByRole('menuitem', { name: '削除' }))
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(deletePost).toHaveBeenCalledWith(2)
    expect(bodies()).toEqual(['投稿1'])
  })

  it('投稿が無いときは「まだ投稿がありません」', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile())
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [], nextCursor: null })
    renderPage()

    expect(await screen.findByText('まだ投稿がありません')).toBeInTheDocument()
  })

  it('一番下までスクロールすると、続きの投稿を自動で読み込む', async () => {
    vi.mocked(fetchUser).mockResolvedValue(profile())
    vi.mocked(fetchUserPosts)
      .mockResolvedValueOnce({ items: [post(3), post(2)], nextCursor: 2 })
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })
    renderPage()
    await screen.findByText('投稿3')

    await io.scrollToBottomWhenObserved()

    expect(await screen.findByText('投稿1')).toBeInTheDocument()
    expect(fetchUserPosts).toHaveBeenLastCalledWith('hanako', 2)
    expect(bodies()).toEqual(['投稿3', '投稿2', '投稿1'])
    expect(io.observedCount()).toBe(0)
  })

  it('存在しない @ユーザーID なら「ユーザーが見つかりません」', async () => {
    vi.mocked(fetchUser).mockRejectedValue(new ApiError(404, { message: 'ユーザーが見つかりません' }))
    vi.mocked(fetchUserPosts).mockRejectedValue(new ApiError(404, { message: 'ユーザーが見つかりません' }))
    renderPage('/users/nobody')

    expect(await screen.findByText('ユーザーが見つかりません')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'ユーザー' })).toBeInTheDocument()
  })

  it('読み込みに失敗したら「再読み込み」で読み直せる', async () => {
    vi.mocked(fetchUser).mockRejectedValueOnce(new ApiError(0, {})).mockResolvedValueOnce(profile())
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByRole('heading', { level: 1, name: '佐藤花子' })).toBeInTheDocument()
  })

  it('タイムラインの投稿者名から、その人のユーザーページへ移る', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(1)], nextCursor: null })
    vi.mocked(fetchUser).mockResolvedValue(profile())
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [post(1)], nextCursor: null })
    const { user } = renderPage('/')

    await user.click(await screen.findByRole('link', { name: '佐藤花子' }))

    expect(await screen.findByRole('region', { name: 'プロフィール' })).toBeInTheDocument()
    expect(fetchUser).toHaveBeenCalledWith('hanako')
  })

  it('ヘッダーの「マイページ」から自分のページへ移る', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(fetchUser).mockResolvedValue(profile({ username: 'taro_dev', name: '山田太郎', isMe: true }))
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderPage('/')

    await user.click(screen.getByRole('link', { name: 'マイページ' }))

    expect(await screen.findByRole('heading', { level: 1, name: '山田太郎' })).toBeInTheDocument()
    expect(fetchUser).toHaveBeenCalledWith('taro_dev')
  })
})
