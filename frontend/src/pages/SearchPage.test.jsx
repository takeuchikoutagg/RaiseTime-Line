import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.js'
import { checkNewPosts, fetchTimeline } from '../api/posts.js'
import { fetchUser, fetchUserPosts, followUser, searchUsers } from '../api/users.js'
import App from '../App.jsx'
import { installIntersectionObserverMock } from '../test/intersectionObserver.js'
import { renderWithAuth } from '../test/renderWithAuth.jsx'

vi.mock('../api/posts.js', () => ({
  fetchTimeline: vi.fn(),
  checkNewPosts: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
  updatePost: vi.fn(),
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
  updateProfile: vi.fn(),
  searchUsers: vi.fn(),
}))

const taro = { username: 'taro_dev', name: '山田太郎', avatarUrl: null }

function summary(username, overrides = {}) {
  return { username, name: `${username}さん`, avatarUrl: null, followedByMe: false, isMe: false, ...overrides }
}

function rows() {
  return within(screen.getByRole('list', { name: '検索結果' }))
    .getAllByRole('listitem')
    .map((row) => within(row).getByText(/^@/).textContent)
}

describe('SearchPage', () => {
  let io

  beforeEach(() => {
    vi.resetAllMocks()
    io = installIntersectionObserverMock()
    vi.mocked(checkNewPosts).mockResolvedValue({ hasNew: false })
    window.scrollTo = vi.fn()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  function renderSearch(route = '/search') {
    return { user: userEvent.setup(), ...renderWithAuth(<App />, { auth: { user: taro }, route }) }
  }

  it('はじめは、何を検索できるかを案内する', () => {
    renderSearch()

    expect(screen.getByRole('heading', { name: 'ユーザー検索' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'ユーザー検索' })).toHaveAttribute(
      'placeholder',
      '🔍 @ユーザーID または 名前で検索',
    )
    expect(screen.getByText('@ユーザーID または名前で、フォローしたいユーザーを探せます')).toBeInTheDocument()
    expect(searchUsers).not.toHaveBeenCalled()
  })

  it('入力が止まってから1回だけ検索し、名前・@ID・フォローボタン（自分は「あなた」）を出す', async () => {
    vi.mocked(searchUsers).mockResolvedValue({
      items: [summary('taro_dev', { name: '山田太郎', isMe: true }), summary('taro0123', { followedByMe: true })],
      nextOffset: null,
    })
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), '@taro')

    expect(await screen.findByRole('list', { name: '検索結果' })).toBeInTheDocument()
    // 1文字ずつ検索せず、入力が止まってから「@」を除いたキーワードで1回だけ
    expect(searchUsers).toHaveBeenCalledTimes(1)
    expect(searchUsers).toHaveBeenCalledWith('taro')
    expect(rows()).toEqual(['@taro_dev', '@taro0123'])
    const [me, other] = within(screen.getByRole('list', { name: '検索結果' })).getAllByRole('listitem')
    expect(within(me).getByText('あなた')).toBeInTheDocument()
    expect(within(me).queryByRole('button')).not.toBeInTheDocument()
    expect(within(other).getByRole('button', { name: /^フォロー中/ })).toBeInTheDocument()
    expect(within(other).getByRole('link', { name: 'taro0123さん' })).toHaveAttribute('href', '/users/taro0123')
  })

  it('該当者がいなければ「該当するユーザーが見つかりません」', async () => {
    vi.mocked(searchUsers).mockResolvedValue({ items: [], nextOffset: null })
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), 'nobody')

    expect(await screen.findByText('該当するユーザーが見つかりません')).toBeInTheDocument()
  })

  it('51文字以上・空白だけ・「@」だけなら検索しない', async () => {
    const { user } = renderSearch()
    const box = screen.getByRole('searchbox', { name: 'ユーザー検索' })

    await user.click(box)
    await user.paste('あ'.repeat(51))
    expect(await screen.findByText('キーワードは50文字以内で入力してください')).toBeInTheDocument()

    await user.clear(box)
    await user.type(box, ' @ ')
    await waitFor(() =>
      expect(screen.getByText('@ユーザーID または名前で、フォローしたいユーザーを探せます')).toBeInTheDocument(),
    )
    expect(searchUsers).not.toHaveBeenCalled()
  })

  it('検索結果からフォローすると、その行のボタンが「フォロー中」になる', async () => {
    vi.mocked(searchUsers).mockResolvedValue({ items: [summary('hanako')], nextOffset: null })
    vi.mocked(followUser).mockResolvedValue({ followedByMe: true, followerCount: 1 })
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), 'hana')
    await user.click(await screen.findByRole('button', { name: 'フォローする' }))

    expect(followUser).toHaveBeenCalledWith('hanako')
    expect(await screen.findByRole('button', { name: /^フォロー中/ })).toBeInTheDocument()
  })

  it('一番下までスクロールすると、続きの結果を自動で読み込む', async () => {
    vi.mocked(searchUsers)
      .mockResolvedValueOnce({ items: [summary('user01'), summary('user02')], nextOffset: 20 })
      .mockResolvedValueOnce({ items: [summary('user21')], nextOffset: null })
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), 'user')
    await screen.findByRole('list', { name: '検索結果' })
    await io.scrollToBottomWhenObserved()

    await waitFor(() => expect(rows()).toEqual(['@user01', '@user02', '@user21']))
    expect(searchUsers).toHaveBeenLastCalledWith('user', 20)
  })

  it('URL のキーワード（?q=）で開くと、すぐに検索する（ユーザーページから戻ったとき）', async () => {
    vi.mocked(searchUsers).mockResolvedValue({ items: [summary('hanako')], nextOffset: null })
    renderSearch('/search?q=hanako')

    expect(screen.getByRole('searchbox', { name: 'ユーザー検索' })).toHaveValue('hanako')
    expect(await screen.findByRole('list', { name: '検索結果' })).toBeInTheDocument()
    expect(searchUsers).toHaveBeenCalledWith('hanako')
  })

  it('検索に失敗したら、メッセージを出す', async () => {
    vi.mocked(searchUsers).mockRejectedValue(new ApiError(0, {}))
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), 'taro')

    expect(await screen.findByText('検索に失敗しました。時間をおいてもう一度お試しください')).toBeInTheDocument()
  })

  it('検索結果の名前から、その人のユーザーページへ移る', async () => {
    vi.mocked(searchUsers).mockResolvedValue({ items: [summary('hanako', { name: '佐藤花子' })], nextOffset: null })
    vi.mocked(fetchUser).mockResolvedValue({
      ...summary('hanako', { name: '佐藤花子' }),
      bio: '',
      followingCount: 0,
      followerCount: 0,
    })
    vi.mocked(fetchUserPosts).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderSearch()

    await user.type(screen.getByRole('searchbox', { name: 'ユーザー検索' }), 'hana')
    await user.click(await screen.findByRole('link', { name: '佐藤花子' }))

    expect(await screen.findByRole('region', { name: 'プロフィール' })).toBeInTheDocument()
    expect(fetchUser).toHaveBeenCalledWith('hanako')
  })

  it('ヘッダーの「検索」から開ける（開いているときは太字）', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderSearch('/')

    await user.click(screen.getByRole('link', { name: '検索' }))

    expect(await screen.findByRole('heading', { name: 'ユーザー検索' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '検索' })).toHaveAttribute('aria-current', 'page')
  })
})
