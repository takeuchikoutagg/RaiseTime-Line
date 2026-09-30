import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client.js'
import { renderWithAuth } from '../test/renderWithAuth.jsx'
import SignupPage from './SignupPage.jsx'

function renderSignup(auth) {
  return renderWithAuth(
    <Routes>
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/" element={<p>ホーム画面</p>} />
    </Routes>,
    { auth, route: '/signup' },
  )
}

async function fillForm(user, overrides = {}) {
  const values = {
    name: '山田太郎',
    username: 'taro_dev',
    email: 'taro@example.com',
    password: 'password123',
    passwordConfirm: 'password123',
    ...overrides,
  }
  // ラベルには「（8〜64文字）」などの補足が付くので、入力欄は id で指定する
  for (const [key, value] of Object.entries(values)) {
    if (value) {
      await user.type(document.getElementById(`signup-${key}`), value)
    }
  }
}

describe('SignupPage', () => {
  it('確認用パスワードが一致しないと、項目の下にエラーを出して API は呼ばない', async () => {
    const user = userEvent.setup()
    const { auth } = renderSignup()

    await fillForm(user, { passwordConfirm: 'different1' })
    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(screen.getByText('パスワード（確認）がパスワードと一致しません')).toBeInTheDocument()
    expect(auth.signup).not.toHaveBeenCalled()
  })

  it('登録に成功したら、確認用パスワードは送らずにホームへ移動する。先頭の @ は取り除く', async () => {
    const user = userEvent.setup()
    const signup = vi.fn().mockResolvedValue()
    renderSignup({ signup })

    await fillForm(user, { username: '@taro_dev' })
    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(signup).toHaveBeenCalledWith({
      name: '山田太郎',
      username: 'taro_dev',
      email: 'taro@example.com',
      password: 'password123',
    })
    expect(await screen.findByText('ホーム画面')).toBeInTheDocument()
  })

  it('サーバーが 409（重複）を返したら、項目ごとにメッセージを表示する', async () => {
    const user = userEvent.setup()
    const signup = vi.fn().mockRejectedValue(
      new ApiError(409, {
        code: 'CONFLICT',
        message: '入力内容に誤りがあります',
        errors: [
          { field: 'username', message: 'この@ユーザーIDはすでに使われています' },
          { field: 'email', message: 'このメールアドレスはすでに登録されています' },
        ],
      }),
    )
    renderSignup({ signup })

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText('この@ユーザーIDはすでに使われています')).toBeInTheDocument()
    expect(screen.getByText('このメールアドレスはすでに登録されています')).toBeInTheDocument()
    expect(document.getElementById('signup-username')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('項目ごとではないエラー（通信失敗など）は、フォームの下に表示する', async () => {
    const user = userEvent.setup()
    const signup = vi.fn().mockRejectedValue(new ApiError(0, { message: '通信に失敗しました。ネットワークを確認してください' }))
    renderSignup({ signup })

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('通信に失敗しました')
  })
})
