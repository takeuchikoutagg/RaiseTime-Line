import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.js'
import { createComment } from '../../api/posts.js'
import CommentForm from './CommentForm.jsx'

vi.mock('../../api/posts.js', () => ({
  createComment: vi.fn(),
}))

describe('CommentForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function setup() {
    const user = userEvent.setup()
    const onCreated = vi.fn()
    render(<CommentForm postId={10} onCreated={onCreated} />)
    return { user, onCreated, textarea: screen.getByLabelText('コメント'), submit: screen.getByRole('button', { name: '送信' }) }
  }

  it('空・空白だけのときは送信できない', async () => {
    const { user, textarea, submit } = setup()

    expect(submit).toBeDisabled()
    await user.type(textarea, '   ')
    expect(submit).toBeDisabled()
    // ゼロ幅スペースだけ（見た目は空）も送信できない
    await user.clear(textarea)
    await user.click(textarea)
    await user.paste('\u200B\u00A0')
    expect(submit).toBeDisabled()
  })

  it('文字数を数え、280文字を超えたら赤くして送信できなくする（絵文字も1文字）', async () => {
    const { user, textarea, submit } = setup()

    await user.click(textarea)
    await user.paste('😀'.repeat(280))
    expect(screen.getByText('280 / 280')).toBeInTheDocument()
    expect(submit).toBeEnabled()

    await user.type(textarea, 'あ')
    expect(screen.getByText('281 / 280').className).toMatch(/over/)
    expect(submit).toBeDisabled()
  })

  it('送信すると onCreated を呼び、入力欄を空にする', async () => {
    const created = { id: 1, body: 'おつかれさま！' }
    vi.mocked(createComment).mockResolvedValue(created)
    const { user, onCreated, textarea, submit } = setup()

    await user.type(textarea, 'おつかれさま！')
    await user.click(submit)

    expect(createComment).toHaveBeenCalledWith(10, 'おつかれさま！')
    expect(onCreated).toHaveBeenCalledWith(created)
    expect(textarea).toHaveValue('')
  })

  it('送信中はボタンを押せない（2重送信を防ぐ）', async () => {
    vi.mocked(createComment).mockReturnValue(new Promise(() => {}))
    const { user, textarea, submit } = setup()

    await user.type(textarea, 'こんにちは')
    await user.click(submit)

    expect(screen.getByRole('button', { name: '送信中…' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '送信中…' }))
    expect(createComment).toHaveBeenCalledTimes(1)
  })

  it('失敗したらメッセージを出し、入力した文は残す', async () => {
    vi.mocked(createComment).mockRejectedValue(new ApiError(404, { message: '投稿が見つかりません' }))
    const { user, onCreated, textarea, submit } = setup()

    await user.type(textarea, 'こんにちは')
    await user.click(submit)

    expect(await screen.findByRole('alert')).toHaveTextContent('投稿が見つかりません')
    expect(textarea).toHaveValue('こんにちは')
    expect(onCreated).not.toHaveBeenCalled()
  })
})
