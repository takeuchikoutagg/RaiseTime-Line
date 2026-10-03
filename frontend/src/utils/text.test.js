import { describe, expect, it } from 'vitest'
import { countChars, hasControlCharacter, hasVisibleText } from './text.js'

describe('countChars', () => {
  it('絵文字も1文字と数える', () => {
    expect(countChars('あ😀a')).toBe(3)
  })
})

describe('hasVisibleText', () => {
  it('普通の文字・絵文字は見える', () => {
    expect(hasVisibleText('こんにちは')).toBe(true)
    expect(hasVisibleText('  a  ')).toBe(true)
    expect(hasVisibleText('😀')).toBe(true)
    // ゼロ幅接合子でつないだ絵文字も、見える文字がある
    expect(hasVisibleText('👨‍👩‍👧')).toBe(true)
  })

  it('空・空白だけ（全角スペースを含む）は見えない', () => {
    expect(hasVisibleText('')).toBe(false)
    expect(hasVisibleText(' \t\n')).toBe(false)
    expect(hasVisibleText('　')).toBe(false)
  })

  it('改行しないスペースやゼロ幅スペースなど、見えない文字だけも見えない（バックエンドと同じ）', () => {
    expect(hasVisibleText(' ')).toBe(false)
    expect(hasVisibleText('  ')).toBe(false)
    expect(hasVisibleText('​‌‍⁠﻿')).toBe(false)
    expect(hasVisibleText(' ​  ')).toBe(false)
  })
})

describe('hasControlCharacter', () => {
  it('改行・タブ・ベル音などの制御文字を含むか（バックエンドと同じ）', () => {
    expect(hasControlCharacter('山田\t太郎')).toBe(true)
    expect(hasControlCharacter('山田\n太郎')).toBe(true)
    expect(hasControlCharacter('ベル\u0007')).toBe(true)
    expect(hasControlCharacter('山田 太郎😀')).toBe(false)
  })
})
