import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.js'
import {
  checkNewPosts,
  createComment,
  createPost,
  deletePost,
  fetchComments,
  fetchPost,
  fetchTimeline,
  likePost,
  updatePost,
} from '../api/posts.js'
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
  updatePost: vi.fn(),
}))

const taro = { username: 'taro_dev', name: '山田太郎' }

function post(overrides = {}) {
  return {
    id: 10,
    body: '詳細の投稿',
    createdAt: '2026-09-26T11:30:00Z',
    editedAt: null,
    author: { username: 'hanako', name: '佐藤花子' },
    images: [],
    likeCount: 2,
    commentCount: 2,
    likedByMe: false,
    isMine: false,
    ...overrides,
  }
}

function comment(id, overrides = {}) {
  return {
    id,
    body: `コメント${id}`,
    createdAt: new Date().toISOString(),
    author: { username: 'ichiro', name: '鈴木一郎' },
    ...overrides,
  }
}

function commentBodies() {
  const list = screen.getByRole('region', { name: 'コメント一覧' })
  return within(list).queryAllByRole('article').map((a) => within(a).getByText(/^コメント\d+$|^新しいコメント$/).textContent)
}

describe('PostDetailPage', () => {
  let io

  beforeEach(() => {
    // 前のテストで使われなかった mockResolvedValueOnce が残らないよう、呼び出し履歴だけでなく返す値も消す
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

  function renderDetail(route = '/posts/10') {
    return { user: userEvent.setup(), ...renderWithAuth(<App />, { auth: { user: taro }, route }) }
  }

  // ---------- 表示 ----------

  it('← 戻る・投稿本体（日時は絶対表示）・コメント欄・コメント一覧（古い順）の順に表示する', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [comment(1), comment(2)], nextCursor: null })
    renderDetail()

    expect(await screen.findByText('詳細の投稿')).toBeInTheDocument()
    expect(fetchPost).toHaveBeenCalledWith('10')
    expect(fetchComments).toHaveBeenCalledWith('10')
    expect(screen.getByRole('button', { name: '← 戻る' })).toBeInTheDocument()
    expect(screen.getByText(/^2026\/09\/26 \d{2}:30$/)).toBeInTheDocument()
    expect(screen.getByLabelText('コメント', { selector: 'textarea' })).toBeInTheDocument()
    expect(commentBodies()).toEqual(['コメント1', 'コメント2'])
    expect(screen.getAllByText('鈴木一郎')).toHaveLength(2)
    // コメントした人の名前は、その人のユーザーページへのリンク
    expect(screen.getAllByRole('link', { name: '鈴木一郎' })[0]).toHaveAttribute('href', '/users/ichiro')
  })

  it('コメントが無いときは「まだコメントはありません」', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post({ commentCount: 0 }))
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    renderDetail()

    expect(await screen.findByText('まだコメントはありません')).toBeInTheDocument()
  })

  it('コメントの本文・コメントした人の名前の HTML は文字のまま表示する（XSS 対策）', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post({ commentCount: 1 }))
    vi.mocked(fetchComments).mockResolvedValue({
      items: [comment(1, { body: '<img src=x onerror=alert(1)>', author: { username: 'ichiro', name: '<b>太字</b>' } })],
      nextCursor: null,
    })
    renderDetail()

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(screen.getByText('<b>太字</b>')).toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    expect(document.querySelector('b')).toBeNull()
  })

  it('投稿が無ければ「投稿が見つかりません」とタイムラインへのリンク', async () => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(404, { message: '投稿が見つかりません' }))
    vi.mocked(fetchComments).mockRejectedValue(new ApiError(404, { message: '投稿が見つかりません' }))
    renderDetail()

    expect(await screen.findByText('投稿が見つかりません')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'タイムラインへ戻る' })).toHaveAttribute('href', '/')
  })

  it('/posts/abc のように id が数字でない URL は、API に問い合わせず「投稿が見つかりません」', async () => {
    renderDetail('/posts/abc')

    expect(await screen.findByText('投稿が見つかりません')).toBeInTheDocument()
    expect(fetchPost).not.toHaveBeenCalled()
    expect(fetchComments).not.toHaveBeenCalled()
  })

  it('API が 400 を返したときも、再読み込みではなく「投稿が見つかりません」', async () => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(400, { message: 'リクエストのパラメーターが正しくありません' }))
    vi.mocked(fetchComments).mockRejectedValue(new ApiError(400, {}))
    renderDetail('/posts/99999999999999999999')

    expect(await screen.findByText('投稿が見つかりません')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '再読み込み' })).not.toBeInTheDocument()
  })

  it('読み込みに失敗したら「再読み込み」で読み直せる', async () => {
    vi.mocked(fetchPost).mockRejectedValueOnce(new ApiError(0, {})).mockResolvedValueOnce(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderDetail()

    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('詳細の投稿')).toBeInTheDocument()
  })

  // ---------- コメント ----------

  it('コメントを送信すると、一覧の末尾に足し、コメント数を+1する', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [comment(1), comment(2)], nextCursor: null })
    vi.mocked(createComment).mockResolvedValue(comment(3, { body: '新しいコメント' }))
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.type(screen.getByLabelText('コメント', { selector: 'textarea' }), '新しいコメント')
    await user.click(screen.getByRole('button', { name: '送信' }))

    expect(createComment).toHaveBeenCalledWith(10, '新しいコメント')
    expect(await screen.findByText('新しいコメント')).toBeInTheDocument()
    expect(commentBodies()).toEqual(['コメント1', 'コメント2', '新しいコメント'])
    expect(screen.getByLabelText(/^コメント \d+件$/, { selector: 'span' })).toHaveTextContent('💬 3')
  })

  it('まだ続きがあるときは、送信したコメントを途中に足さない（スクロールで最後まで読むと出る）', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post({ commentCount: 21 }))
    vi.mocked(fetchComments)
      .mockResolvedValueOnce({ items: [comment(1)], nextCursor: 1 })
      .mockResolvedValueOnce({ items: [comment(2), comment(3, { body: '新しいコメント' })], nextCursor: null })
    vi.mocked(createComment).mockResolvedValue(comment(3, { body: '新しいコメント' }))
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.type(screen.getByLabelText('コメント', { selector: 'textarea' }), '新しいコメント')
    await user.click(screen.getByRole('button', { name: '送信' }))

    expect(screen.getByLabelText(/^コメント \d+件$/, { selector: 'span' })).toHaveTextContent('💬 22')
    expect(commentBodies()).toEqual(['コメント1'])

    await io.scrollToBottomWhenObserved()
    expect(await screen.findByText('新しいコメント')).toBeInTheDocument()
    expect(commentBodies()).toEqual(['コメント1', 'コメント2', '新しいコメント'])
  })

  it('一番下までスクロールすると、続きのコメントを自動で読み込む', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments)
      .mockResolvedValueOnce({ items: [comment(1), comment(2)], nextCursor: 2 })
      .mockResolvedValueOnce({ items: [comment(3)], nextCursor: null })
    renderDetail()
    await screen.findByText('コメント1')

    await io.scrollToBottomWhenObserved()

    expect(await screen.findByText('コメント3')).toBeInTheDocument()
    expect(fetchComments).toHaveBeenLastCalledWith('10', 2)
    expect(commentBodies()).toEqual(['コメント1', 'コメント2', 'コメント3'])
    // 最後まで読み込んだら、それ以上は見張らない
    expect(io.observedCount()).toBe(0)
  })

  it('続きの読み込みに失敗したら「再読み込み」ボタンを出す', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments)
      .mockResolvedValueOnce({ items: [comment(1)], nextCursor: 1 })
      .mockRejectedValueOnce(new ApiError(0, {}))
      .mockResolvedValueOnce({ items: [comment(2)], nextCursor: null })
    const { user } = renderDetail()
    await screen.findByText('コメント1')

    await io.scrollToBottomWhenObserved()
    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('コメント2')).toBeInTheDocument()
  })

  // ---------- いいね・削除・戻る ----------

  it('投稿詳細でもいいねできる', async () => {
    vi.mocked(likePost).mockResolvedValue({ likeCount: 3, likedByMe: true })
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.click(screen.getByRole('button', { name: /^いいね/ }))

    expect(likePost).toHaveBeenCalledWith(10)
    expect(screen.getByRole('button', { name: /^いいね/ })).toHaveTextContent('♥ 3')
  })

  it('自分の投稿を編集すると、投稿本体が変わり「編集済み 日時」が出る（コメントはそのまま）', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post({ isMine: true, author: taro }))
    vi.mocked(fetchComments).mockResolvedValue({ items: [comment(1)], nextCursor: null })
    vi.mocked(updatePost).mockResolvedValue(
      post({ isMine: true, author: taro, body: '直した本文', editedAt: '2026-09-26T12:05:00Z' }),
    )
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.click(screen.getByRole('button', { name: 'メニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '編集' }))
    await user.clear(screen.getByLabelText('本文'))
    await user.type(screen.getByLabelText('本文'), '直した本文')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('直した本文')).toBeInTheDocument()
    expect(screen.getByText(/^編集済み 2026\/09\/26 \d{2}:05$/)).toBeInTheDocument()
    expect(commentBodies()).toEqual(['コメント1'])
  })

  it('自分の投稿を削除したら、タイムラインへ戻る', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post({ isMine: true, author: taro }))
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(deletePost).mockResolvedValue()
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.click(screen.getByRole('button', { name: 'メニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(deletePost).toHaveBeenCalledWith(10)
    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })

  it('URL を直接開いたときの「← 戻る」はタイムラインへ', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderDetail()

    await user.click(await screen.findByRole('button', { name: '← 戻る' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })

  it('タイムラインの 💬 から開いて「← 戻る」で、タイムラインに戻る', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post()], nextCursor: null })
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    const { user } = renderDetail('/')

    await user.click(await screen.findByRole('link', { name: /^コメント \d+件$/ }))
    await user.click(await screen.findByRole('button', { name: '← 戻る' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })

  it('投稿詳細でヘッダーの「投稿」から投稿すると、タイムラインへ移り、投稿が1回だけ出る', async () => {
    const created = post({ id: 99, body: '詳細から投稿', isMine: true, author: taro, likeCount: 0, commentCount: 0 })
    vi.mocked(fetchPost).mockResolvedValue(post())
    vi.mocked(fetchComments).mockResolvedValue({ items: [], nextCursor: null })
    vi.mocked(createPost).mockResolvedValue(created)
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [created, post()], nextCursor: null })
    const { user } = renderDetail()
    await screen.findByText('詳細の投稿')

    await user.click(screen.getByRole('button', { name: '投稿' }))
    await user.type(screen.getByLabelText('本文'), '詳細から投稿')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
    await screen.findAllByText('詳細から投稿')
    expect(screen.getAllByText('詳細から投稿')).toHaveLength(1)
  })
})
