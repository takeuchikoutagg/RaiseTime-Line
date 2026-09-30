import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App.jsx'
import { renderWithAuth } from './test/renderWithAuth.jsx'

const taro = { username: 'taro_dev', name: '山田太郎' }

describe('App（ログイン状態による画面の出し分け）', () => {
  it('ログインしていないとき、ホームを開くとログイン画面になる', () => {
    renderWithAuth(<App />, { route: '/' })

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('ログイン中は、仮のホーム画面に Hello World と名前を表示する', () => {
    renderWithAuth(<App />, { auth: { user: taro }, route: '/' })

    expect(screen.getByRole('heading', { name: 'Hello World' })).toBeInTheDocument()
    expect(screen.getByText('ようこそ、山田太郎（@taro_dev）さん')).toBeInTheDocument()
  })

  it('ログイン中にログイン画面・新規登録画面を開くとホームへ移動する', () => {
    renderWithAuth(<App />, { auth: { user: taro }, route: '/signup' })

    expect(screen.getByRole('heading', { name: 'Hello World' })).toBeInTheDocument()
  })

  it('ログイン状態を確認している間は「読み込み中」を表示する', () => {
    renderWithAuth(<App />, { auth: { status: 'loading' }, route: '/' })

    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')
  })

  it('ホームのログアウトボタンで logout を呼ぶ', async () => {
    const user = userEvent.setup()
    const logout = vi.fn().mockResolvedValue()
    renderWithAuth(<App />, { auth: { user: taro, logout }, route: '/' })

    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('存在しない URL はホーム（未ログインならログイン画面）へ', () => {
    renderWithAuth(<App />, { route: '/no-such-page' })

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})
