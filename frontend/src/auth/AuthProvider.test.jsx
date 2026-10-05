import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

describe('AuthProvider（ログイン・ログアウトなどの操作）', () => {
  let fetchMock

  // ログイン状態を表示し、ボタンで login などを呼ぶだけの部品
  function Controls() {
    const { status, user, login, signup, logout, updateUser } = useAuth()
    if (status === 'loading') return <p>確認中</p>
    return (
      <>
        <p>{user ? `ログイン中：${user.username}（${user.name}）` : '未ログイン'}</p>
        <button type="button" onClick={() => login({ email: 'taro@example.com', password: 'password123' })}>
          ログイン
        </button>
        <button type="button" onClick={() => signup({ name: '新人', username: 'newbie', email: 'n@example.com', password: 'password123' })}>
          登録
        </button>
        <button type="button" onClick={() => logout().catch(() => {})}>
          ログアウト
        </button>
        <button type="button" onClick={() => updateUser({ name: '山田花子' })}>
          名前を変える
        </button>
      </>
    )
  }

  function renderControls() {
    render(
      <AuthProvider>
        <Controls />
      </AuthProvider>,
    )
    return userEvent.setup()
  }

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('login・signup に成功すると、返ってきたユーザーでログイン中になる', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
    const user = renderControls()
    expect(await screen.findByText('未ログイン')).toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))
    await user.click(screen.getByRole('button', { name: 'ログイン' }))
    expect(await screen.findByText('ログイン中：taro_dev（山田太郎）')).toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(jsonResponse(201, { username: 'newbie', name: '新人' }))
    await user.click(screen.getByRole('button', { name: '登録' }))
    expect(await screen.findByText('ログイン中：newbie（新人）')).toBeInTheDocument()
  })

  it('updateUser で、ログイン中のユーザーの一部だけを書き換える', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))
    const user = renderControls()
    expect(await screen.findByText('ログイン中：taro_dev（山田太郎）')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '名前を変える' }))

    expect(screen.getByText('ログイン中：taro_dev（山田花子）')).toBeInTheDocument()
  })

  it('logout は、通信に失敗しても画面はログアウトした状態にする', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))
    const user = renderControls()
    expect(await screen.findByText('ログイン中：taro_dev（山田太郎）')).toBeInTheDocument()

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await user.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(await screen.findByText('未ログイン')).toBeInTheDocument()
  })

  it('useAuth を AuthProvider の外で使うとエラーにする（使い方の間違いに気づけるように）', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => render(<ShowUser />)).toThrow('useAuth は AuthProvider の中で使ってください')

    vi.mocked(console.error).mockRestore()
  })
})
