import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPost, updatePost } from '../../api/posts.js'
import { ApiError } from '../../api/client.js'
import ComposeModal, { DISCARD_CONFIRM_MESSAGE } from './ComposeModal.jsx'

vi.mock('../../api/posts.js', () => ({ createPost: vi.fn(), updatePost: vi.fn() }))

function image(name, type = 'image/png', size = 10) {
  return new File([new Uint8Array(size)], name, { type })
}

describe('ComposeModal', () => {
  beforeEach(() => {
    // jsdom には URL.createObjectURL が無いので用意する
    let n = 0
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => `blob:preview-${n++}`),
      revokeObjectURL: vi.fn(),
    }))
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  function setup() {
    const onClose = vi.fn()
    const onCreated = vi.fn()
    render(<ComposeModal onClose={onClose} onCreated={onCreated} />)
    return { user: userEvent.setup({ applyAccept: false }), onClose, onCreated }
  }

  it('本文が空のあいだは投稿できない。文字数を数える（絵文字も1文字）', async () => {
    const { user } = setup()
    const submit = screen.getByRole('button', { name: '投稿する' })

    expect(submit).toBeDisabled()
    await user.type(screen.getByLabelText('本文'), '😀😀')
    expect(screen.getByText('2 / 280')).toBeInTheDocument()
    expect(submit).toBeEnabled()
  })

  it('280文字を超えると、カウンターが警告になり投稿できない', async () => {
    const { user } = setup()

    await user.click(screen.getByLabelText('本文'))
    await user.paste('あ'.repeat(281))

    expect(screen.getByText('281 / 280')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  it('画像を選ぶとプレビューが出て、×で外せる', async () => {
    const { user } = setup()

    await user.upload(screen.getByTestId('image-input'), [image('a.png'), image('b.jpg', 'image/jpeg')])
    expect(screen.getAllByRole('img')).toHaveLength(2)
    expect(screen.getByText('画像を追加（2/4）')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '画像1を外す' }))
    expect(screen.getAllByRole('img')).toHaveLength(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-0')
  })

  it('5枚目・対象外の形式・5MB 超えは追加せず、メッセージを出す', async () => {
    const { user } = setup()

    await user.upload(screen.getByTestId('image-input'), [image('1.png'), image('2.png'), image('3.png'), image('4.png')])
    expect(screen.getAllByRole('img')).toHaveLength(4)
    expect(screen.getByTestId('image-input')).toBeDisabled()

    await user.click(screen.getByRole('button', { name: '画像4を外す' }))
    await user.upload(screen.getByTestId('image-input'), [image('a.bmp', 'image/bmp')])
    expect(screen.getByRole('alert')).toHaveTextContent('JPEG・PNG・GIF・WebP の画像を選んでください')

    await user.upload(screen.getByTestId('image-input'), [image('big.png', 'image/png', 5 * 1024 * 1024 + 1)])
    expect(screen.getByRole('alert')).toHaveTextContent('画像は1枚5MBまでです')
    expect(screen.getAllByRole('img')).toHaveLength(3)
  })

  it('ブラウザが表示できない（中身が画像でない）ファイルは外して、メッセージを出す', async () => {
    const { user } = setup()

    await user.upload(screen.getByTestId('image-input'), [image('photo.jpg', 'image/jpeg')])
    fireEvent.error(screen.getByRole('img', { name: '選んだ画像1' }))

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('JPEG・PNG・GIF・WebP の画像を選んでください')
  })

  it('投稿すると、本文と画像を送って onCreated を呼ぶ', async () => {
    const { user, onCreated } = setup()
    const created = { id: 1, body: 'こんにちは' }
    vi.mocked(createPost).mockResolvedValue(created)
    const file = image('a.png')

    await user.type(screen.getByLabelText('本文'), 'こんにちは')
    await user.upload(screen.getByTestId('image-input'), [file])
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(createPost).toHaveBeenCalledWith({ body: 'こんにちは', images: [file] })
    expect(onCreated).toHaveBeenCalledWith(created)
  })

  it('サーバーのエラー（画像の中身が不正など）はメッセージを出し、もう一度送れる', async () => {
    const { user, onCreated } = setup()
    vi.mocked(createPost).mockRejectedValue(
      new ApiError(400, { message: 'JPEG・PNG・GIF・WebP の画像を選んでください' }),
    )

    await user.type(screen.getByLabelText('本文'), 'こんにちは')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('JPEG・PNG・GIF・WebP の画像を選んでください')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿する' })).toBeEnabled()
    expect(onCreated).not.toHaveBeenCalled()
  })

  it('×・Esc で閉じる', async () => {
    const { user, onClose } = setup()

    await user.click(screen.getByRole('button', { name: '閉じる' }))
    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledTimes(2)
  })

  // ---------- 編集モード（F-10） ----------

  describe('編集モード', () => {
    const post = {
      id: 7,
      body: '編集前の本文',
      images: [
        { id: 71, url: 'http://img/posts/7/a.png', position: 1 },
        { id: 72, url: 'http://img/posts/7/b.png', position: 2 },
      ],
    }

    function setupEdit() {
      const onClose = vi.fn()
      const onSaved = vi.fn()
      render(<ComposeModal post={post} onClose={onClose} onSaved={onSaved} />)
      return { user: userEvent.setup({ applyAccept: false }), onClose, onSaved }
    }

    it('タイトルは「投稿を編集」で、今の本文と画像が入った状態で開く', () => {
      setupEdit()

      expect(screen.getByRole('dialog', { name: '投稿を編集' })).toBeInTheDocument()
      expect(screen.getByLabelText('本文')).toHaveValue('編集前の本文')
      expect(screen.getAllByRole('img').map((img) => img.getAttribute('src'))).toEqual([
        'http://img/posts/7/a.png',
        'http://img/posts/7/b.png',
      ])
      expect(screen.getByText('画像を追加（2/4）')).toBeInTheDocument()
    })

    it('何も変えていないときは「保存する」を押せず、本文を変えると押せる', async () => {
      const { user } = setupEdit()
      const save = screen.getByRole('button', { name: '保存する' })

      expect(save).toBeDisabled()
      await user.type(screen.getByLabelText('本文'), '！')
      expect(save).toBeEnabled()
      // 元に戻すと、また押せない
      await user.type(screen.getByLabelText('本文'), '{Backspace}')
      expect(save).toBeDisabled()
    })

    it('画像を外して保存すると、残す画像の id と本文を送り、onSaved に編集後の投稿を渡す', async () => {
      const saved = { ...post, body: '編集後', editedAt: '2026-10-03T00:00:00Z' }
      vi.mocked(updatePost).mockResolvedValue(saved)
      const { user, onSaved } = setupEdit()

      await user.click(screen.getByRole('button', { name: '画像1を外す' }))
      await user.clear(screen.getByLabelText('本文'))
      await user.type(screen.getByLabelText('本文'), '編集後')
      await user.click(screen.getByRole('button', { name: '保存する' }))

      expect(updatePost).toHaveBeenCalledWith(7, { body: '編集後', keepImageIds: [72], images: [] })
      expect(onSaved).toHaveBeenCalledWith(saved)
      expect(createPost).not.toHaveBeenCalled()
      // 今付いている画像のプレビュー URL（S3 の URL）は解放しない
      expect(URL.revokeObjectURL).not.toHaveBeenCalledWith('http://img/posts/7/a.png')
    })

    it('画像を足すと、今の画像と合わせて4枚まで。足した画像はファイルとして送る', async () => {
      vi.mocked(updatePost).mockResolvedValue(post)
      const { user } = setupEdit()

      await user.upload(screen.getByTestId('image-input'), [image('c.png'), image('d.png'), image('e.png')])

      expect(screen.getAllByRole('img')).toHaveLength(4)
      expect(screen.getByRole('alert')).toHaveTextContent('画像は4枚まで添付できます')
      await user.click(screen.getByRole('button', { name: '保存する' }))
      const [, payload] = vi.mocked(updatePost).mock.calls[0]
      expect(payload.keepImageIds).toEqual([71, 72])
      expect(payload.images.map((file) => file.name)).toEqual(['c.png', 'd.png'])
    })

    it('変更があるまま閉じようとすると確認し、「キャンセル」なら閉じない・「OK」なら閉じる', async () => {
      const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
      const { user, onClose } = setupEdit()

      await user.type(screen.getByLabelText('本文'), '！')
      await user.click(screen.getByRole('button', { name: '閉じる' }))
      expect(confirm).toHaveBeenCalledWith(DISCARD_CONFIRM_MESSAGE)
      expect(onClose).not.toHaveBeenCalled()

      await user.keyboard('{Escape}')
      expect(onClose).toHaveBeenCalledTimes(1)
      confirm.mockRestore()
    })

    it('何も変えていなければ、確認せずに閉じる', async () => {
      const confirm = vi.spyOn(window, 'confirm')
      const { user, onClose } = setupEdit()

      await user.click(screen.getByRole('button', { name: '閉じる' }))

      expect(confirm).not.toHaveBeenCalled()
      expect(onClose).toHaveBeenCalled()
      confirm.mockRestore()
    })

    it('他人の投稿・削除済みなどで失敗したら、メッセージを出して開いたままにする', async () => {
      vi.mocked(updatePost).mockRejectedValue(new ApiError(403, { message: 'この投稿は編集できません' }))
      const { user, onSaved } = setupEdit()

      await user.type(screen.getByLabelText('本文'), '！')
      await user.click(screen.getByRole('button', { name: '保存する' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('この投稿は編集できません')
      expect(onSaved).not.toHaveBeenCalled()
      expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled()
    })
  })
})
