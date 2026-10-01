import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { checkNewPosts, createPost, deletePost, fetchTimeline } from './posts.js'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('posts API', () => {
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

  it('fetchTimeline：before を付けて呼べる', async () => {
    // Response の本文は1回しか読めないので、呼ばれるたびに作る
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { items: [], nextCursor: null })))

    await fetchTimeline()
    await fetchTimeline(40)

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/timeline', '/api/timeline?before=40'])
  })

  it('checkNewPosts：after を付けて呼ぶ', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { hasNew: true }))

    await expect(checkNewPosts(5)).resolves.toEqual({ hasNew: true })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/timeline/has-new?after=5')
  })

  it('createPost：multipart/form-data（FormData）で本文と画像を送り、Content-Type は自分で付けない', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { id: 1 }))
    const image = new File(['x'], 'a.png', { type: 'image/png' })

    await createPost({ body: 'こんにちは', images: [image, image] })

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/posts')
    expect(options.method).toBe('POST')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('body')).toBe('こんにちは')
    expect(options.body.getAll('images')).toHaveLength(2)
    // 区切り文字（boundary）つきの Content-Type はブラウザが付ける
    expect(options.headers['Content-Type']).toBeUndefined()
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })

  it('deletePost：DELETE で呼び、CSRF ヘッダーを付ける', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await deletePost(10)

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/posts/10')
    expect(options.method).toBe('DELETE')
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })
})
