import { fireEvent, render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Avatar from './Avatar.jsx'

describe('Avatar', () => {
  it('プロフィール画像が無ければ、名前の1文字目を色つきの丸で出す', () => {
    const { container } = render(<Avatar username="taro_dev" name="山田太郎" />)

    expect(container.textContent).toBe('山')
    expect(container.querySelector('img')).toBeNull()
  })

  it('プロフィール画像があれば、画像を出す（読み上げには出さない）', () => {
    const { container } = render(<Avatar username="taro_dev" name="山田太郎" avatarUrl="http://img/avatars/1/a.png" />)

    expect(container.querySelector('img')).toHaveAttribute('src', 'http://img/avatars/1/a.png')
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true')
  })

  it('画像を読み込めなかったら、名前の1文字目の丸に戻す', () => {
    const { container } = render(<Avatar username="taro_dev" name="山田太郎" avatarUrl="http://img/broken.png" />)

    fireEvent.error(container.querySelector('img'))

    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toBe('山')
  })
})
