import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  checkNewPosts,
  createComment,
  createPost,
  deletePost,
  fetchComments,
  fetchPost,
  fetchTimeline,
  likePost,
  unlikePost,
  updatePost,
} from './posts.js'

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

  it('fetchPost：投稿詳細を GET で取る', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 10 }))

    await expect(fetchPost(10)).resolves.toEqual({ id: 10 })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/posts/10')
  })

  it('likePost / unlikePost：PUT / DELETE で呼び、CSRF ヘッダーを付ける', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { likeCount: 1, likedByMe: true })))

    await expect(likePost(10)).resolves.toEqual({ likeCount: 1, likedByMe: true })
    await unlikePost(10)

    expect(fetchMock.mock.calls.map(([path, options]) => [path, options.method])).toEqual([
      ['/api/posts/10/like', 'PUT'],
      ['/api/posts/10/like', 'DELETE'],
    ])
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })

  it('fetchComments：after を付けて呼べる', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { items: [], nextCursor: null })))

    await fetchComments(10)
    await fetchComments(10, 88)

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/posts/10/comments',
      '/api/posts/10/comments?after=88',
    ])
  })

  it('createComment：JSON で本文を送る', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { id: 1 }))

    await createComment(10, 'おつかれさま！')

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/posts/10/comments')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({ body: 'おつかれさま！' })
  })

  it('updatePost：PATCH の multipart/form-data で、本文・残す画像の id・追加する画像を送る', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 7 }))
    const image = new File(['x'], 'c.png', { type: 'image/png' })

    await updatePost(7, { body: '編集後', keepImageIds: [72, 71], images: [image] })

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/posts/7')
    expect(options.method).toBe('PATCH')
    expect(options.body.get('body')).toBe('編集後')
    expect(options.body.getAll('keepImageIds')).toEqual(['72', '71'])
    expect(options.body.getAll('images')).toHaveLength(1)
    expect(options.headers['Content-Type']).toBeUndefined()
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })
})
