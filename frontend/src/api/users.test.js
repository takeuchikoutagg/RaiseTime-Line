import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchUser, fetchUserPosts, followUser, unfollowUser, updateProfile } from './users.js'

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

  it('updateProfile：PATCH の multipart/form-data で、表示名・@ID・自己紹介・画像・画像を外すかを送る', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { username: 'taro_new' })))
    const avatar = new File(['x'], 'me.png', { type: 'image/png' })

    await updateProfile({ name: '山田', username: 'taro_new', bio: 'よろしく', avatar })
    await updateProfile({ name: '山田', username: 'taro_new', bio: '', removeAvatar: true })

    const [path, options] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/users/me')
    expect(options.method).toBe('PATCH')
    expect(options.body.get('name')).toBe('山田')
    expect(options.body.get('username')).toBe('taro_new')
    expect(options.body.get('bio')).toBe('よろしく')
    expect(options.body.get('avatar')).toBeInstanceOf(File)
    expect(options.body.get('removeAvatar')).toBeNull()
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-123')
    const [, second] = fetchMock.mock.calls[1]
    expect(second.body.get('avatar')).toBeNull()
    expect(second.body.get('removeAvatar')).toBe('true')
  })
})
