import { describe, expect, it } from 'vitest'
import { AVATAR_SIZE, centerSquare, fitWithin, POST_IMAGE_MAX_SIDE, resizeForPost, smallerOf } from './image.js'

describe('fitWithin（長い辺を上限に収める）', () => {
  it('大きな写真は、縦横比を保ったまま長い辺を上限にする', () => {
    expect(fitWithin(4032, 3024, POST_IMAGE_MAX_SIDE)).toEqual({ width: 1600, height: 1200 })
    expect(fitWithin(3024, 4032, POST_IMAGE_MAX_SIDE)).toEqual({ width: 1200, height: 1600 })
  })

  it('上限より小さい画像は大きくしない', () => {
    expect(fitWithin(800, 600, POST_IMAGE_MAX_SIDE)).toEqual({ width: 800, height: 600 })
  })
})

describe('centerSquare（真ん中を正方形に切り抜く）', () => {
  it('横長なら左右、縦長なら上下を切る', () => {
    expect(centerSquare(4032, 3024)).toEqual({ sx: 504, sy: 0, side: 3024 })
    expect(centerSquare(3024, 4032)).toEqual({ sx: 0, sy: 504, side: 3024 })
    expect(centerSquare(500, 500)).toEqual({ sx: 0, sy: 0, side: 500 })
  })

  it('プロフィール画像は 400×400px にする', () => {
    expect(AVATAR_SIZE).toBe(400)
  })
})

describe('resizeForPost', () => {
  it('アニメーション GIF は、動きが消えないよう縮めずにそのまま返す', async () => {
    const gif = new File([new Uint8Array(10)], 'anim.gif', { type: 'image/gif' })

    await expect(resizeForPost(gif)).resolves.toBe(gif)
  })
})

describe('smallerOf', () => {
  it('縮めた JPEG のほうが大きくなったら（図やスクリーンショットなど）、元のファイルを送る', () => {
    const png = new File([new Uint8Array(500)], 'chart.png', { type: 'image/png' })
    const biggerJpeg = new File([new Uint8Array(900)], 'chart.jpg', { type: 'image/jpeg' })
    const smallerJpeg = new File([new Uint8Array(100)], 'photo.jpg', { type: 'image/jpeg' })

    expect(smallerOf(png, biggerJpeg)).toBe(png)
    expect(smallerOf(png, smallerJpeg)).toBe(smallerJpeg)
  })
})
