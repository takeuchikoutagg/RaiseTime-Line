import { describe, expect, it } from 'vitest'
import { countChars, validateSignup } from './signup.js'

const valid = {
  name: '山田太郎',
  username: 'taro_dev',
  email: 'taro@example.com',
  password: 'password123',
  passwordConfirm: 'password123',
}

describe('countChars', () => {
  it('絵文字も1文字と数える', () => {
    expect(countChars('😀😀')).toBe(2)
    expect('😀😀'.length).toBe(4)
  })
})

describe('validateSignup', () => {
  it('正しい入力ならエラーなし', () => {
    expect(validateSignup(valid)).toEqual({})
  })

  it('名前：空白だけ・51文字はエラー、絵文字50文字はOK', () => {
    expect(validateSignup({ ...valid, name: '   ' })).toHaveProperty('name')
    expect(validateSignup({ ...valid, name: 'あ'.repeat(51) })).toHaveProperty('name')
    expect(validateSignup({ ...valid, name: '😀'.repeat(50) })).toEqual({})
    // 名前は1行で表示するので、タブなどの制御文字はエラー
    expect(validateSignup({ ...valid, name: '山田\t太郎' })).toHaveProperty('name')
  })

  it('@ユーザーID：3〜15文字の半角英小文字・数字・_ だけ', () => {
    for (const username of ['ab', 'a'.repeat(16), 'Taro', 'taro-dev', '']) {
      expect(validateSignup({ ...valid, username })).toHaveProperty('username')
    }
    expect(validateSignup({ ...valid, username: 'abc' })).toEqual({})
  })

  it('メールアドレスの形式', () => {
    expect(validateSignup({ ...valid, email: 'not-an-email' })).toHaveProperty('email')
    expect(validateSignup({ ...valid, email: '' })).toHaveProperty('email')
  })

  it('パスワード：8〜64文字、UTF-8 で72バイトまで', () => {
    expect(validateSignup({ ...valid, password: '1234567', passwordConfirm: '1234567' })).toHaveProperty('password')
    const tooManyBytes = 'あ'.repeat(25)
    expect(validateSignup({ ...valid, password: tooManyBytes, passwordConfirm: tooManyBytes }).password).toBe(
      'パスワードが長すぎます。全角文字を減らしてください',
    )
  })

  it('確認用パスワードが一致しない', () => {
    expect(validateSignup({ ...valid, passwordConfirm: 'different' })).toEqual({
      passwordConfirm: 'パスワード（確認）がパスワードと一致しません',
    })
  })
})
