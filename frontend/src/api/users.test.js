import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchUser, fetchUserPosts, followUser, unfollowUser } from './users.js'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('users API', () => {
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

  it('fetchUser：@ユーザーID を URL に入れて GET する', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { username: 'hanako' }))

    await expect(fetchUser('hanako')).resolves.toEqual({ username: 'hanako' })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/users/hanako')
  })

  it('fetchUserPosts：before を付けて呼べる', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { items: [], nextCursor: null })))

    await fetchUserPosts('hanako')
    await fetchUserPosts('hanako', 40)

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/users/hanako/posts',
      '/api/users/hanako/posts?before=40',
    ])
  })

  it('followUser / unfollowUser：PUT / DELETE で呼び、CSRF ヘッダーを付ける', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { followedByMe: true, followerCount: 1 })))

    await expect(followUser('hanako')).resolves.toEqual({ followedByMe: true, followerCount: 1 })
    await unfollowUser('hanako')

    expect(fetchMock.mock.calls.map(([path, options]) => [path, options.method])).toEqual([
      ['/api/users/hanako/follow', 'PUT'],
      ['/api/users/hanako/follow', 'DELETE'],
    ])
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('csrf-123')
  })
})
