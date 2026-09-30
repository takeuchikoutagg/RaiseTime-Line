import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request, setUnauthorizedHandler } from './client.js'

function jsonResponse(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('request', () => {
  let fetchMock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-123'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => {})
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('POST では XSRF-TOKEN Cookie の値を X-XSRF-TOKEN ヘッダーで送り、Cookie も送る', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }))

    await request('/api/auth/login', { method: 'POST', body: { email: 'a@example.com' } })

    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(options.credentials).toBe('same-origin')
    expect(JSON.parse(options.body)).toEqual({ email: 'a@example.com' })
  })

  it('GET では X-XSRF-TOKEN ヘッダーを付けない', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}))

    await request('/api/auth/me')

    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('401 なら refresh を呼んでから1回だけ呼び直す', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev' })) // refresh
      .mockResolvedValueOnce(jsonResponse(200, { username: 'taro_dev', name: '山田太郎' }))

    const result = await request('/api/auth/me')

    expect(result).toEqual({ username: 'taro_dev', name: '山田太郎' })
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me',
      '/api/auth/refresh',
      '/api/auth/me',
    ])
    expect(fetchMock.mock.calls[1][1].method).toBe('POST')
  })

  it('refresh にも失敗したら、ログアウト扱いの処理を呼んで 401 を投げる', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED', message: 'ログインしてください' }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' })) // refresh

    await expect(request('/api/auth/me')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('同時に2つの API が 401 になっても、refresh は1回だけ呼ぶ', async () => {
    let resolveRefresh
    fetchMock.mockImplementation((path) => {
      if (path === '/api/auth/refresh') {
        return new Promise((resolve) => {
          resolveRefresh = () => resolve(jsonResponse(200, {}))
        })
      }
      return Promise.resolve(jsonResponse(fetchMock.mock.calls.filter(([p]) => p === path).length > 1 ? 200 : 401, {}))
    })

    const both = Promise.all([request('/api/a'), request('/api/b')])
    await vi.waitFor(() => expect(resolveRefresh).toBeTypeOf('function'))
    resolveRefresh()
    await both

    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
  })

  it('ログイン失敗（認証の API の 401）では refresh しない', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { code: 'UNAUTHORIZED', message: 'メールアドレスまたはパスワードが正しくありません' }),
    )

    const error = await request('/api/auth/login', { method: 'POST', body: {} }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toBe('メールアドレスまたはパスワードが正しくありません')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('エラーの JSON（code・message・errors）を ApiError にして投げる', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        code: 'CONFLICT',
        message: '入力内容に誤りがあります',
        errors: [{ field: 'username', message: 'この@ユーザーIDはすでに使われています' }],
      }),
    )

    const error = await request('/api/auth/signup', { method: 'POST', body: {} }).catch((e) => e)

    expect(error).toMatchObject({ status: 409, code: 'CONFLICT' })
    expect(error.errors).toEqual([{ field: 'username', message: 'この@ユーザーIDはすでに使われています' }])
  })

  it('204 のときは undefined を返す', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(request('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('通信そのものに失敗したら、わかりやすいメッセージの ApiError にする', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(request('/api/auth/me')).rejects.toMatchObject({
      status: 0,
      message: '通信に失敗しました。ネットワークを確認してください',
    })
  })
})
