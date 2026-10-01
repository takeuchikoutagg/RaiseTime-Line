import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.js'
import { deletePost } from '../../api/posts.js'
import DeleteConfirmModal from './DeleteConfirmModal.jsx'

vi.mock('../../api/posts.js', () => ({ deletePost: vi.fn() }))

const POST = { id: 10 }

function setup() {
  const onClose = vi.fn()
  const onDeleted = vi.fn()
  render(<DeleteConfirmModal post={POST} onClose={onClose} onDeleted={onDeleted} />)
  return { user: userEvent.setup(), onClose, onDeleted }
}

describe('DeleteConfirmModal', () => {
  afterEach(() => vi.clearAllMocks())

  it('元に戻せないこと・コメントといいねも消えることを伝える', () => {
    setup()

    expect(screen.getByRole('alertdialog', { name: 'この投稿を削除しますか？' })).toBeInTheDocument()
    expect(screen.getByText(/元に戻せません/)).toBeInTheDocument()
    expect(screen.getByText(/コメントといいねも削除されます/)).toBeInTheDocument()
  })

  it('「削除する」で API を呼び、onDeleted を呼ぶ', async () => {
    const { user, onDeleted } = setup()
    vi.mocked(deletePost).mockResolvedValue()

    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(deletePost).toHaveBeenCalledWith(10)
    expect(onDeleted).toHaveBeenCalledWith(10)
  })

  it('すでに削除されていた（404）ときも、画面から消す', async () => {
    const { user, onDeleted } = setup()
    vi.mocked(deletePost).mockRejectedValue(new ApiError(404, { message: '投稿が見つかりません' }))

    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(onDeleted).toHaveBeenCalledWith(10)
  })

  it('他人の投稿（403）などはメッセージを出し、閉じない', async () => {
    const { user, onDeleted } = setup()
    vi.mocked(deletePost).mockRejectedValue(new ApiError(403, { message: 'この投稿は削除できません' }))

    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('この投稿は削除できません')).toBeInTheDocument()
    expect(onDeleted).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '削除する' })).toBeEnabled()
  })

  it('キャンセルでは何もせず閉じる', async () => {
    const { user, onClose } = setup()

    await user.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(onClose).toHaveBeenCalled()
    expect(deletePost).not.toHaveBeenCalled()
  })
})
