import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.js'
import { renderWithAuth } from '../test/renderWithAuth.jsx'
import LoginPage from './LoginPage.jsx'

function renderLogin(auth) {
  return renderWithAuth(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>ホーム画面</p>} />
    </Routes>,
    { auth, route: '/login' },
  )
}

describe('LoginPage', () => {
  it('未入力で送ると、メッセージを出して API は呼ばない', async () => {
    const user = userEvent.setup()
    const { auth } = renderLogin()

    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(screen.getByRole('alert')).toHaveTextContent('メールアドレスとパスワードを入力してください')
    expect(auth.login).not.toHaveBeenCalled()
  })

  it('ログインに成功したらホームへ移動する', async () => {
    const user = userEvent.setup()
    const login = vi.fn().mockResolvedValue()
    renderLogin({ login })

    await user.type(screen.getByLabelText('メールアドレス'), 'taro@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'password123')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(login).toHaveBeenCalledWith({ email: 'taro@example.com', password: 'password123' })
    expect(await screen.findByText('ホーム画面')).toBeInTheDocument()
  })

  it('ログインに失敗したら、サーバーのメッセージを表示してその画面に留まる', async () => {
    const user = userEvent.setup()
    const login = vi.fn().mockRejectedValue(
      new ApiError(401, { code: 'UNAUTHORIZED', message: 'メールアドレスまたはパスワードが正しくありません' }),
    )
    renderLogin({ login })

    await user.type(screen.getByLabelText('メールアドレス'), 'taro@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByText('メールアドレスまたはパスワードが正しくありません')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled()
  })

  it('新規登録画面へのリンクがある', () => {
    renderLogin()

    expect(screen.getByRole('link', { name: '新規登録' })).toHaveAttribute('href', '/signup')
  })

  // 状態遷移（送信中）：通信が終わるまでボタンを押せない（2重送信を防ぐ。docs/テスト設計書.md T-17）
  it('ログイン中はボタンを押せず、失敗したらもう一度押せる', async () => {
    const user = userEvent.setup()
    let fail
    const login = vi.fn().mockReturnValue(new Promise((_, reject) => (fail = reject)))
    renderLogin({ login })

    await user.type(screen.getByLabelText('メールアドレス'), 'taro@example.com')
    await user.type(screen.getByLabelText('パスワード'), 'password123')
    await user.click(screen.getByRole('button', { name: 'ログイン' }))

    const submitting = screen.getByRole('button', { name: 'ログイン中…' })
    expect(submitting).toBeDisabled()
    await user.click(submitting)
    expect(login).toHaveBeenCalledTimes(1)

    fail(new ApiError(401, { message: 'メールアドレスまたはパスワードが正しくありません' }))
    expect(await screen.findByRole('button', { name: 'ログイン' })).toBeEnabled()
  })
})
