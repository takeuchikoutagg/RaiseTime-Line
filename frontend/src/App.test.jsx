import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchTimeline } from './api/posts.js'
import App from './App.jsx'
import { renderWithAuth } from './test/renderWithAuth.jsx'

vi.mock('./api/posts.js', () => ({
  fetchTimeline: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
}))

const taro = { username: 'taro_dev', name: '山田太郎' }

describe('App（ログイン状態による画面の出し分け）', () => {
  beforeEach(() => {
    vi.mocked(fetchTimeline).mockResolvedValue({ items: [], nextCursor: null })
  })

  it('ログインしていないとき、ホームを開くとログイン画面になる', () => {
    renderWithAuth(<App />, { route: '/' })

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('ログイン中は、共通ヘッダーとタイムラインを表示する', async () => {
    renderWithAuth(<App />, { auth: { user: taro }, route: '/' })

    expect(screen.getByRole('navigation', { name: 'メイン' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
    expect(await screen.findByText('まだ投稿がありません。最初の投稿をしてみましょう')).toBeInTheDocument()
  })

  it('ログイン中にログイン画面・新規登録画面を開くとホームへ移動する', () => {
    renderWithAuth(<App />, { auth: { user: taro }, route: '/signup' })

    expect(screen.getByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })

  it('ログイン状態を確認している間は「読み込み中」を表示する', () => {
    renderWithAuth(<App />, { auth: { status: 'loading' }, route: '/' })

    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')
  })

  it('ヘッダーのログアウトボタンで logout を呼ぶ', async () => {
    const user = userEvent.setup()
    const logout = vi.fn().mockResolvedValue()
    renderWithAuth(<App />, { auth: { user: taro, logout }, route: '/' })

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('ヘッダーの「投稿」ボタンで投稿モーダルが開く', async () => {
    const user = userEvent.setup()
    renderWithAuth(<App />, { auth: { user: taro }, route: '/' })

    await user.click(screen.getByRole('button', { name: '投稿' }))

    expect(screen.getByRole('dialog', { name: '新しい投稿' })).toBeInTheDocument()
  })

  it('存在しない URL はホーム（未ログインならログイン画面）へ', () => {
    renderWithAuth(<App />, { route: '/no-such-page' })

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})
