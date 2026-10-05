import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchCsrf, fetchMe, login, logout, signup } from './auth.js'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('auth API', () => {
  let fetchMock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-123'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('fetchCsrf・fetchMe：GET で呼ぶ', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))

    await fetchCsrf()
    const me = await fetchMe()

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/csrf', '/api/auth/me'])
    expect(me).toEqual({ username: 'taro_dev', name: '山田太郎' })
  })

  it('login：メールアドレスとパスワードだけを JSON で POST し、CSRF ヘッダーを付ける', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))

    await login({ email: 'taro@example.com', password: 'password123', extra: 'x' })

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/auth/login')
    expect(options.method).toBe('POST')
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
    expect(JSON.parse(options.body)).toEqual({ email: 'taro@example.com', password: 'password123' })
  })

  it('signup：名前・@ユーザーID・メールアドレス・パスワードだけを送る（確認用パスワードは送らない）', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { username: 'newbie', name: '新人' }))

    await signup({
      name: '新人',
      username: 'newbie',
      email: 'new@example.com',
      password: 'password123',
      passwordConfirm: 'password123',
    })

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/auth/signup')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({
      name: '新人',
      username: 'newbie',
      email: 'new@example.com',
      password: 'password123',
    })
  })

  it('logout：POST で呼び、CSRF ヘッダーを付ける', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await logout()

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/auth/logout')
    expect(options.method).toBe('POST')
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })

  it('ログインに失敗したら（401）、サーバーのメッセージのエラーを投げ、refresh は呼ばない', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(401, { code: 'UNAUTHORIZED', message: 'メールアドレスまたはパスワードが正しくありません', errors: [] }),
    )

    await expect(login({ email: 'taro@example.com', password: 'wrong' })).rejects.toThrow(
      'メールアドレスまたはパスワードが正しくありません',
    )
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
