import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuthProvider from './AuthProvider.jsx'
import { useAuth } from './useAuth.js'

function jsonResponse(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function ShowUser() {
  const { status, user } = useAuth()
  if (status === 'loading') return <p>確認中</p>
  return <p>{user ? `ログイン中：${user.username}` : '未ログイン'}</p>
}

describe('AuthProvider（画面を開いたときのログイン状態の確認）', () => {
  let fetchMock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('csrf → me の順に呼び、me が成功すればログイン中になる', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))

    render(
      <AuthProvider>
        <ShowUser />
      </AuthProvider>,
    )

    expect(await screen.findByText('ログイン中：taro_dev')).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/csrf', '/api/auth/me'])
  })

  it('アクセストークンが切れていても、refresh に成功すればログイン中になる', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))

    render(
      <AuthProvider>
        <ShowUser />
      </AuthProvider>,
    )

    expect(await screen.findByText('ログイン中：taro_dev')).toBeInTheDocument()
  })

  it('refresh にも失敗したら未ログインになる', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))

    render(
      <AuthProvider>
        <ShowUser />
      </AuthProvider>,
    )

    expect(await screen.findByText('未ログイン')).toBeInTheDocument()
  })
})
