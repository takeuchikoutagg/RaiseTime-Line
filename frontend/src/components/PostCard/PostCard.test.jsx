import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import PostCard from './PostCard.jsx'

function post(overrides = {}) {
  return {
    id: 10,
    body: 'こんにちは\n2行目',
    createdAt: new Date(Date.now() - 3 * 60 * 1000).toISOString(),
    editedAt: null,
    author: { username: 'taro_dev', name: '山田太郎' },
    images: [],
    isMine: false,
    ...overrides,
  }
}

describe('PostCard', () => {
  it('名前・@ユーザーID・相対時刻・本文を表示する', () => {
    render(<PostCard post={post()} onDeleteRequest={vi.fn()} />)

    expect(screen.getByText('山田太郎')).toBeInTheDocument()
    expect(screen.getByText('@taro_dev')).toBeInTheDocument()
    expect(screen.getByText('3分前')).toBeInTheDocument()
    expect(screen.getByText(/こんにちは/)).toBeInTheDocument()
    // インプレッション数・リツイートは出さない
    expect(screen.queryByText(/インプレッション|リツイート/)).not.toBeInTheDocument()
  })

  it('本文の HTML は文字のまま表示する（XSS 対策）', () => {
    const { container } = render(
      <PostCard post={post({ body: '<img src=x onerror=alert(1)>' })} onDeleteRequest={vi.fn()} />,
    )

    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(container.querySelector('img[src="x"]')).toBeNull()
  })

  it('画像を表示順に出す', () => {
    const images = [
      { id: 1, url: 'http://img/1.png', position: 1 },
      { id: 2, url: 'http://img/2.png', position: 2 },
    ]
    render(<PostCard post={post({ images })} onDeleteRequest={vi.fn()} />)

    expect(screen.getAllByRole('img').map((img) => img.getAttribute('src'))).toEqual([
      'http://img/1.png',
      'http://img/2.png',
    ])
  })

  it('他人の投稿には「…」メニューを出さない', () => {
    render(<PostCard post={post({ isMine: false })} onDeleteRequest={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'メニュー' })).not.toBeInTheDocument()
  })

  it('自分の投稿は「…」→「削除」で onDeleteRequest を呼ぶ', async () => {
    const user = userEvent.setup()
    const onDeleteRequest = vi.fn()
    const mine = post({ isMine: true })
    render(<PostCard post={mine} onDeleteRequest={onDeleteRequest} />)

    await user.click(screen.getByRole('button', { name: 'メニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))

    expect(onDeleteRequest).toHaveBeenCalledWith(mine)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('編集した投稿には「編集済み」を出す', () => {
    render(<PostCard post={post({ editedAt: new Date().toISOString() })} onDeleteRequest={vi.fn()} />)

    expect(screen.getByText('· 編集済み')).toBeInTheDocument()
  })
})
