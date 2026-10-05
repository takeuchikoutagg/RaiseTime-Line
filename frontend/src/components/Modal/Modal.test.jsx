import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import Modal from './Modal.jsx'

function renderModal(onClose = vi.fn(), children = <input aria-label="本文" />) {
  render(
    <Modal title="投稿を作成" onClose={onClose}>
      {children}
    </Modal>,
  )
  return { onClose, user: userEvent.setup() }
}

describe('Modal', () => {
  it('タイトルを名前にした dialog として開き、入力欄にフォーカスを移す（「閉じる」ボタンより優先）', () => {
    renderModal()

    expect(screen.getByRole('dialog', { name: '投稿を作成' })).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByRole('textbox', { name: '本文' })).toHaveFocus()
  })

  it('操作できる部品が「閉じる」ボタンだけなら、そこにフォーカスを移す', () => {
    renderModal(vi.fn(), <p>説明だけ</p>)

    expect(screen.getByRole('button', { name: '閉じる' })).toHaveFocus()
  })

  it('variant="alert" なら alertdialog にする（削除の確認など）', () => {
    render(
      <Modal title="投稿を削除しますか？" onClose={vi.fn()} variant="alert">
        <button type="button">削除する</button>
      </Modal>,
    )

    expect(screen.getByRole('alertdialog', { name: '投稿を削除しますか？' })).toBeInTheDocument()
  })

  it('Tab・Shift+Tab では、フォーカスがモーダルの中だけを回る', async () => {
    const { user } = renderModal(
      vi.fn(),
      <>
        <input aria-label="本文" />
        <button type="button">投稿する</button>
      </>,
    )
    const close = screen.getByRole('button', { name: '閉じる' })
    const submit = screen.getByRole('button', { name: '投稿する' })

    // 最後の「投稿する」から Tab で、先頭の「閉じる」へ戻る
    submit.focus()
    await user.tab()
    expect(close).toHaveFocus()

    // 先頭の「閉じる」から Shift+Tab で、最後の「投稿する」へ
    await user.tab({ shift: true })
    expect(submit).toHaveFocus()
  })

  it('Esc・×・背景のクリックで閉じる。モーダルの中のクリックでは閉じない', async () => {
    const { onClose, user } = renderModal()

    await user.click(screen.getByRole('textbox', { name: '本文' }))
    expect(onClose).not.toHaveBeenCalled()

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)

    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(onClose).toHaveBeenCalledTimes(2)

    // 背景（dialog の外側の要素）
    await user.click(screen.getByRole('dialog').parentElement)
    expect(onClose).toHaveBeenCalledTimes(3)
  })

  it('閉じたら、開く前にフォーカスがあったボタンにフォーカスを戻す', async () => {
    function Page() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            投稿
          </button>
          {open && (
            <Modal title="投稿を作成" onClose={() => setOpen(false)}>
              <input aria-label="本文" />
            </Modal>
          )}
        </>
      )
    }
    render(<Page />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: '投稿' }))
    expect(screen.getByRole('textbox', { name: '本文' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿' })).toHaveFocus()
  })
})
