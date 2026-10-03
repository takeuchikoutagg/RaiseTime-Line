import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.js'
import { followUser, unfollowUser } from '../../api/users.js'
import FollowButton, { FOLLOW_ERROR_DURATION_MS } from './FollowButton.jsx'

vi.mock('../../api/users.js', () => ({
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
}))

// ユーザーページと同じく、onChange で受け取った値でボタンを切り替える
function Harness({ initial = false, onChange = vi.fn() }) {
  const [followedByMe, setFollowedByMe] = useState(initial)
  return (
    <FollowButton
      username="hanako"
      followedByMe={followedByMe}
      onChange={(result) => {
        setFollowedByMe(result.followedByMe)
        onChange(result)
      }}
    />
  )
}

describe('FollowButton', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('未フォローなら「フォローする」。押すとフォローして「フォロー中」になり、フォロワー数を知らせる', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    vi.mocked(followUser).mockResolvedValue({ followedByMe: true, followerCount: 16 })
    render(<Harness onChange={onChange} />)

    await user.click(screen.getByRole('button', { name: 'フォローする' }))

    expect(followUser).toHaveBeenCalledWith('hanako')
    expect(onChange).toHaveBeenCalledWith({ followedByMe: true, followerCount: 16 })
    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
  })

  it('フォロー中なら「フォロー中」。押すと確認なしで解除して「フォローする」に戻る', async () => {
    const user = userEvent.setup()
    vi.mocked(unfollowUser).mockResolvedValue({ followedByMe: false, followerCount: 15 })
    render(<Harness initial />)

    await user.click(screen.getByRole('button', { name: 'フォロー中' }))

    expect(unfollowUser).toHaveBeenCalledWith('hanako')
    expect(followUser).not.toHaveBeenCalled()
    expect(await screen.findByRole('button', { name: 'フォローする' })).toBeInTheDocument()
  })

  it('フォロー中のボタンには、マウスを乗せたとき用の「フォロー解除」も入っている（読み上げには出さない）', () => {
    render(<Harness initial />)

    expect(screen.getByText('フォロー解除')).toHaveAttribute('aria-hidden', 'true')
  })

  it('通信中は押せない', async () => {
    const user = userEvent.setup()
    vi.mocked(followUser).mockReturnValue(new Promise(() => {}))
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'フォローする' }))

    expect(screen.getByRole('button', { name: 'フォローする' })).toBeDisabled()
  })

  it('失敗したらメッセージを出し（3秒で消える）、ボタンはそのまま', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.mocked(followUser).mockRejectedValue(new ApiError(404, { message: 'ユーザーが見つかりません' }))
    render(<Harness />)

    await user.click(screen.getByRole('button', { name: 'フォローする' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('ユーザーが見つかりません')
    expect(screen.getByRole('button', { name: 'フォローする' })).toBeEnabled()
    await act(() => vi.advanceTimersByTimeAsync(FOLLOW_ERROR_DURATION_MS))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
