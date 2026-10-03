import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.js'
import { updateProfile } from '../../api/users.js'
import { resizeForAvatar } from '../../utils/image.js'
import ProfileEditModal from './ProfileEditModal.jsx'

vi.mock('../../api/users.js', () => ({ updateProfile: vi.fn() }))
// jsdom では画像を読み込めないので、切り抜いて縮める処理は置き換える
vi.mock('../../utils/image.js', () => ({ resizeForAvatar: vi.fn() }))

const profile = {
  username: 'taro_dev',
  name: '山田太郎',
  bio: 'Javaを勉強中',
  avatarUrl: 'http://img/avatars/1/a.png',
  followingCount: 1,
  followerCount: 2,
  followedByMe: false,
  isMe: true,
}

function image(name, type = 'image/png') {
  return new File([new Uint8Array(10)], name, { type })
}

describe('ProfileEditModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    let n = 0
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => `blob:avatar-${n++}`),
      revokeObjectURL: vi.fn(),
    }))
    // 400×400px の JPEG に縮めた（ことにする）
    vi.mocked(resizeForAvatar).mockImplementation(async (file) =>
      new File([new Uint8Array(5)], file.name.replace(/\.[^.]*$/, '.jpg'), { type: 'image/jpeg' }),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function setup(overrides = {}) {
    const onClose = vi.fn()
    const onSaved = vi.fn()
    render(<ProfileEditModal profile={{ ...profile, ...overrides }} onClose={onClose} onSaved={onSaved} />)
    return { user: userEvent.setup({ applyAccept: false }), onClose, onSaved }
  }

  it('今の表示名・@ユーザーID・自己紹介・画像が入った状態で開き、何も変えていなければ保存できない', () => {
    setup()

    expect(screen.getByRole('dialog', { name: 'プロフィールを編集' })).toBeInTheDocument()
    expect(screen.getByLabelText(/^名前/)).toHaveValue('山田太郎')
    expect(screen.getByLabelText(/^@ユーザーID/)).toHaveValue('taro_dev')
    expect(screen.getByLabelText('自己紹介')).toHaveValue('Javaを勉強中')
    expect(screen.getByText('8 / 160')).toBeInTheDocument()
    expect(document.querySelector('img')).toHaveAttribute('src', 'http://img/avatars/1/a.png')
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('表示名・@ユーザーID・自己紹介を変えて保存すると、その内容を送って onSaved に渡す（名前の前後の空白は取る）', async () => {
    const saved = { ...profile, name: '山田（改）', username: 'taro_new', bio: '2行目\nもある' }
    vi.mocked(updateProfile).mockResolvedValue(saved)
    const { user, onSaved } = setup()

    await user.clear(screen.getByLabelText(/^名前/))
    await user.type(screen.getByLabelText(/^名前/), ' 山田（改） ')
    await user.clear(screen.getByLabelText(/^@ユーザーID/))
    await user.type(screen.getByLabelText(/^@ユーザーID/), 'taro_new')
    expect(screen.getByText('@ユーザーIDを変えると、今のページの URL は使えなくなります')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('自己紹介'))
    await user.type(screen.getByLabelText('自己紹介'), '2行目{Enter}もある')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(updateProfile).toHaveBeenCalledWith({
      name: '山田（改）',
      username: 'taro_new',
      bio: '2行目\nもある',
      avatar: null,
      removeAvatar: false,
    })
    expect(onSaved).toHaveBeenCalledWith(saved)
  })

  it('入力が正しくなければ送らず、項目の下にメッセージを出す', async () => {
    const { user } = setup()

    await user.clear(screen.getByLabelText(/^名前/))
    await user.clear(screen.getByLabelText(/^@ユーザーID/))
    await user.type(screen.getByLabelText(/^@ユーザーID/), 'Taro')
    await user.click(screen.getByLabelText('自己紹介'))
    await user.paste('あ'.repeat(160))
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(updateProfile).not.toHaveBeenCalled()
    expect(screen.getByText('名前は1〜50文字で入力してください')).toBeInTheDocument()
    expect(screen.getByText('@ユーザーIDは3〜15文字の半角英小文字・数字・_で入力してください')).toBeInTheDocument()
    expect(screen.getByText('自己紹介は160文字以内で入力してください')).toBeInTheDocument()
    expect(screen.getByText('168 / 160').className).toMatch(/over/)
    expect(screen.getByLabelText(/^@ユーザーID/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('画像を変更すると、正方形に縮めた画像のプレビューになり、それを送る', async () => {
    vi.mocked(updateProfile).mockResolvedValue(profile)
    const { user } = setup()
    const original = image('me.png')

    await user.upload(screen.getByTestId('avatar-input'), original)

    expect(resizeForAvatar).toHaveBeenCalledWith(original)
    await waitFor(() => expect(document.querySelector('img')).toHaveAttribute('src', 'blob:avatar-0'))
    await user.click(screen.getByRole('button', { name: '保存する' }))
    const [payload] = vi.mocked(updateProfile).mock.calls[0]
    expect(payload.avatar.name).toBe('me.jpg')
    expect(payload.avatar.type).toBe('image/jpeg')
    expect(payload.removeAvatar).toBe(false)
  })

  it('読み込めない（中身が画像でない）ファイルは使わず、メッセージを出す', async () => {
    vi.mocked(resizeForAvatar).mockRejectedValueOnce(new Error('画像を読み込めませんでした'))
    const { user } = setup()

    await user.upload(screen.getByTestId('avatar-input'), image('fake.png'))

    expect(await screen.findByText('JPEG・PNG・GIF・WebP の画像を選んでください')).toBeInTheDocument()
    expect(document.querySelector('img')).toHaveAttribute('src', 'http://img/avatars/1/a.png')
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('画像以外のファイルは選べない', async () => {
    const { user } = setup()

    await user.upload(screen.getByTestId('avatar-input'), image('a.txt', 'text/plain'))

    expect(screen.getByText('JPEG・PNG・GIF・WebP の画像を選んでください')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('「画像を外す」で、名前の1文字目の丸に戻り、removeAvatar を送る', async () => {
    vi.mocked(updateProfile).mockResolvedValue({ ...profile, avatarUrl: null })
    const { user } = setup()

    await user.click(screen.getByRole('button', { name: '画像を外す' }))

    expect(document.querySelector('img')).toBeNull()
    expect(screen.queryByRole('button', { name: '画像を外す' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '保存する' }))
    expect(vi.mocked(updateProfile).mock.calls[0][0]).toMatchObject({ avatar: null, removeAvatar: true })
  })

  it('@ユーザーID がほかの人と重なっていたら（409）、@ユーザーID の下にメッセージを出して開いたままにする', async () => {
    vi.mocked(updateProfile).mockRejectedValue(
      new ApiError(409, {
        message: '入力内容に誤りがあります',
        errors: [{ field: 'username', message: 'この@ユーザーIDはすでに使われています' }],
      }),
    )
    const { user, onSaved } = setup()

    await user.clear(screen.getByLabelText(/^@ユーザーID/))
    await user.type(screen.getByLabelText(/^@ユーザーID/), 'hanako')
    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('この@ユーザーIDはすでに使われています')).toBeInTheDocument()
    expect(onSaved).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled()
  })

  it('変更があるまま閉じようとすると確認し、何も変えていなければ確認しない', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user, onClose } = setup()

    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(confirm).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalledTimes(1)

    await user.type(screen.getByLabelText('自己紹介'), '！')
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(confirm).toHaveBeenCalledWith('変更を破棄しますか？')
    expect(onClose).toHaveBeenCalledTimes(1)
    confirm.mockRestore()
  })
})
