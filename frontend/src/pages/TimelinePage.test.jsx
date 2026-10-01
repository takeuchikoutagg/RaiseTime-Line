import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPost, deletePost, fetchTimeline } from '../api/posts.js'
import App from '../App.jsx'
import { renderWithAuth } from '../test/renderWithAuth.jsx'

vi.mock('../api/posts.js', () => ({
  fetchTimeline: vi.fn(),
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

function renderTimeline() {
  return { user: userEvent.setup(), ...renderWithAuth(<App />, { auth: { user: taro }, route: '/' }) }
}

describe('TimelinePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('投稿を新しい順に表示する', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(3), post(2)], nextCursor: null })
    renderTimeline()

    const articles = await screen.findAllByRole('article')
    expect(articles.map((a) => within(a).getByText(/投稿\d/).textContent)).toEqual(['投稿3', '投稿2'])
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument()
  })

  it('「もっと見る」で続きを読み込み、末尾に足す', async () => {
    vi.mocked(fetchTimeline)
      .mockResolvedValueOnce({ items: [post(3)], nextCursor: 3 })
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })
    const { user } = renderTimeline()

    await user.click(await screen.findByRole('button', { name: 'もっと見る' }))

    expect(fetchTimeline).toHaveBeenLastCalledWith(3)
    expect(await screen.findByText('投稿1')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument()
  })

  it('読み込みに失敗したら「再読み込み」で読み直せる', async () => {
    vi.mocked(fetchTimeline)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })
    const { user } = renderTimeline()

    await user.click(await screen.findByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('投稿1')).toBeInTheDocument()
  })

  it('投稿すると、モーダルが閉じて先頭に出る（読み込み直さない）', async () => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [post(1)], nextCursor: null })
    vi.mocked(createPost).mockResolvedValue(post(2, { body: '新しい投稿' }))
    const { user } = renderTimeline()
    await screen.findByText('投稿1')

    await user.click(screen.getByRole('button', { name: 'いまどうしてる？' }))
    await user.type(screen.getByLabelText('本文'), '新しい投稿')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const articles = screen.getAllByRole('article')
    expect(within(articles[0]).getByText('新しい投稿')).toBeInTheDocument()
    expect(articles).toHaveLength(2)
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
    expect(screen.queryByText('投稿2')).not.toBeInTheDocument()
    expect(screen.getByText('投稿1')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
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
