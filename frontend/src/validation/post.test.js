import { describe, expect, it } from 'vitest'
import { IMAGE_TOO_LARGE, IMAGE_TYPE_MESSAGE, TOO_MANY_IMAGES, checkImages, isValidBody } from './post.js'

function file(name, type, size = 10) {
  return new File([new Uint8Array(size)], name, { type })
}

describe('isValidBody', () => {
  it('空・空白だけは不可、絵文字280文字は OK、281文字は不可', () => {
    expect(isValidBody('')).toBe(false)
    expect(isValidBody('   ')).toBe(false)
    expect(isValidBody('こんにちは')).toBe(true)
    expect(isValidBody('😀'.repeat(280))).toBe(true)
    expect(isValidBody('😀'.repeat(281))).toBe(false)
  })
})

describe('checkImages', () => {
  it('JPEG・PNG・GIF・WebP を受け付ける', () => {
    const files = [file('a.jpg', 'image/jpeg'), file('b.png', 'image/png'), file('c.gif', 'image/gif')]

    expect(checkImages(files, 0)).toEqual({ accepted: files, error: '' })
  })

  it('対象外の形式は外してメッセージを返す', () => {
    const ok = file('a.png', 'image/png')

    const result = checkImages([file('a.bmp', 'image/bmp'), ok, file('a.txt', 'text/plain')], 0)

    expect(result.accepted).toEqual([ok])
    expect(result.error).toBe(IMAGE_TYPE_MESSAGE)
  })

  it('5MB を超える画像は外す', () => {
    const result = checkImages([file('big.png', 'image/png', 5 * 1024 * 1024 + 1)], 0)

    expect(result.accepted).toEqual([])
    expect(result.error).toBe(IMAGE_TOO_LARGE)
  })

  it('すでに選んだ枚数と合わせて4枚まで', () => {
    const files = [file('1.png', 'image/png'), file('2.png', 'image/png'), file('3.png', 'image/png')]

    const result = checkImages(files, 2)

    expect(result.accepted).toEqual(files.slice(0, 2))
    expect(result.error).toBe(TOO_MANY_IMAGES)
  })
})
