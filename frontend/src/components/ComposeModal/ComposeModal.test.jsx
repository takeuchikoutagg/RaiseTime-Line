import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPost } from '../../api/posts.js'
import { ApiError } from '../../api/client.js'
import ComposeModal from './ComposeModal.jsx'

vi.mock('../../api/posts.js', () => ({ createPost: vi.fn() }))

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
})
