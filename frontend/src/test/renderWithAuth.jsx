import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { AuthContext } from '../auth/authContext.js'

// ログイン状態を自由に決めて画面を表示するためのテスト用の関数
export function renderWithAuth(ui, { auth = {}, route = '/' } = {}) {
  const value = {
    status: 'ready',
    user: null,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
    updateUser: vi.fn(),
    ...auth,
  }
  const result = render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </AuthContext.Provider>,
  )
  return { ...result, auth: value }
}
