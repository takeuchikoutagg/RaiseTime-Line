import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { likePost, unlikePost } from '../../api/posts.js'
import { ApiError } from '../../api/client.js'
import PostCard from './PostCard.jsx'

vi.mock('../../api/posts.js', () => ({
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}))

function post(overrides = {}) {
  return {
    id: 10,
    body: 'こんにちは\n2行目',
    createdAt: new Date(Date.now() - 3 * 60 * 1000).toISOString(),
    editedAt: null,
    author: { username: 'taro_dev', name: '山田太郎' },
    images: [],
    likeCount: 12,
    commentCount: 3,
    likedByMe: false,
    isMine: false,
    ...overrides,
  }
}

// 親（タイムラインなど）と同じく、onUpdate で受け取った変更を投稿に反映して表示し直す
function Harness({ initial, detail = false, onDeleteRequest = vi.fn() }) {
  const [current, setCurrent] = useState(initial)
  return (
    <PostCard
      post={current}
      detail={detail}
      onDeleteRequest={onDeleteRequest}
      onUpdate={(id, changes) => setCurrent((p) => (p.id === id ? { ...p, ...changes } : p))}
    />
  )
}

function renderCard(initial = post(), options = {}) {
  return render(
    <MemoryRouter>
      <Harness initial={initial} {...options} />
    </MemoryRouter>,
  )
}

function likeButton() {
  return screen.getByRole('button', { name: 'いいね' })
}

describe('PostCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('名前・@ユーザーID・相対時刻・本文・いいね数・コメント数を表示する', () => {
    renderCard()

    expect(screen.getByText('山田太郎')).toBeInTheDocument()
    expect(screen.getByText('@taro_dev')).toBeInTheDocument()
    expect(screen.getByText('3分前')).toBeInTheDocument()
    expect(screen.getByText(/こんにちは/)).toBeInTheDocument()
    expect(likeButton()).toHaveTextContent('♡ 12')
    expect(screen.getByRole('link', { name: 'コメント' })).toHaveTextContent('💬 3')
    // インプレッション数・リツイートは出さない
    expect(screen.queryByText(/インプレッション|リツイート/)).not.toBeInTheDocument()
  })

  it('本文とコメント数は、投稿詳細へのリンクになる', () => {
    renderCard()

    expect(screen.getByText(/こんにちは/).closest('a')).toHaveAttribute('href', '/posts/10')
    expect(screen.getByRole('link', { name: 'コメント' })).toHaveAttribute('href', '/posts/10')
  })

  it('本文の HTML は文字のまま表示する（XSS 対策）', () => {
    const { container } = renderCard(post({ body: '<img src=x onerror=alert(1)>' }))

    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(container.querySelector('img[src="x"]')).toBeNull()
  })

  it('画像を表示順に出す', () => {
    const images = [
      { id: 1, url: 'http://img/1.png', position: 1 },
      { id: 2, url: 'http://img/2.png', position: 2 },
    ]
    renderCard(post({ images }))

    expect(screen.getAllByRole('img').map((img) => img.getAttribute('src'))).toEqual([
      'http://img/1.png',
      'http://img/2.png',
    ])
  })

  it('他人の投稿には「…」メニューを出さない', () => {
    renderCard(post({ isMine: false }))

    expect(screen.queryByRole('button', { name: 'メニュー' })).not.toBeInTheDocument()
  })

  it('自分の投稿は「…」→「削除」で onDeleteRequest を呼ぶ', async () => {
    const user = userEvent.setup()
    const onDeleteRequest = vi.fn()
    const mine = post({ isMine: true })
    renderCard(mine, { onDeleteRequest })

    await user.click(screen.getByRole('button', { name: 'メニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))

    expect(onDeleteRequest).toHaveBeenCalledWith(mine)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('編集した投稿には「編集済み」を出す', () => {
    renderCard(post({ editedAt: new Date().toISOString() }))

    expect(screen.getByText('· 編集済み')).toBeInTheDocument()
  })

  // ---------- 投稿詳細での表示 ----------

  it('投稿詳細では、日時を絶対表示にし、本文はリンクにしない。編集済みなら最終編集日時も出す', () => {
    renderCard(post({ createdAt: '2026-09-26T11:30:00Z', editedAt: '2026-09-26T12:05:00Z' }), { detail: true })

    expect(screen.getByText(/^2026\/09\/26 \d{2}:30$/)).toBeInTheDocument()
    expect(screen.getByText(/^編集済み 2026\/09\/26 \d{2}:05$/)).toBeInTheDocument()
    expect(screen.getByText(/こんにちは/).closest('a')).toBeNull()
    expect(screen.queryByRole('link', { name: 'コメント' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('コメント')).toHaveTextContent('💬 3')
  })

  // ---------- いいね ----------

  it('いいねを押すと、すぐ ♥ と数+1 になり、サーバーの値に合わせる', async () => {
    const user = userEvent.setup()
    let resolve
    vi.mocked(likePost).mockReturnValue(new Promise((r) => (resolve = r)))
    renderCard()

    await user.click(likeButton())

    // サーバーの返事を待たずに切り替わる（楽観的更新）
    expect(likeButton()).toHaveTextContent('♥ 13')
    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')
    expect(likePost).toHaveBeenCalledWith(10)

    // サーバーの値（ほかの人のいいねも含めた数）で合わせ直す
    resolve({ likeCount: 20, likedByMe: true })
    await waitFor(() => expect(likeButton()).toHaveTextContent('♥ 20'))
  })

  it('いいね済みで押すと解除する', async () => {
    const user = userEvent.setup()
    vi.mocked(unlikePost).mockResolvedValue({ likeCount: 11, likedByMe: false })
    renderCard(post({ likedByMe: true }))

    await user.click(likeButton())

    expect(unlikePost).toHaveBeenCalledWith(10)
    expect(likePost).not.toHaveBeenCalled()
    await waitFor(() => expect(likeButton()).toHaveTextContent('♡ 11'))
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
  })

  it('失敗したら元に戻し、「いいねできませんでした」と出す', async () => {
    const user = userEvent.setup()
    vi.mocked(likePost).mockRejectedValue(new ApiError(0, { message: '通信に失敗しました' }))
    renderCard()

    await user.click(likeButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('いいねできませんでした')
    expect(likeButton()).toHaveTextContent('♡ 12')
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
  })

  it('投稿が削除されていたら（404）、元に戻して「投稿が見つかりません」と出す', async () => {
    const user = userEvent.setup()
    vi.mocked(likePost).mockRejectedValue(new ApiError(404, { message: '投稿が見つかりません' }))
    renderCard()

    await user.click(likeButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('投稿が見つかりません')
    expect(likeButton()).toHaveTextContent('♡ 12')
  })

  it('通信中に連打しても、リクエストは1回だけ', async () => {
    const user = userEvent.setup()
    vi.mocked(likePost).mockReturnValue(new Promise(() => {}))
    renderCard()

    await user.click(likeButton())
    await user.click(likeButton())
    await user.click(likeButton())

    expect(likePost).toHaveBeenCalledTimes(1)
    expect(unlikePost).not.toHaveBeenCalled()
    expect(likeButton()).toHaveTextContent('♥ 13')
  })
})
