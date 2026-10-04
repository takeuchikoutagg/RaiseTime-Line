import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.js'
import { validateSignup } from '../validation/signup.js'
import styles from './AuthPage.module.css'

// S-02 新規登録（docs/機能定義書/F-01_新規登録.md）
const EMPTY = { name: '', username: '', email: '', password: '', passwordConfirm: '' }

export default function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    // 先頭の @ は入力されていても取り除く
    const input = { ...values, username: values.username.trim().replace(/^@/, '') }
    const clientErrors = validateSignup(input)
    setErrors(clientErrors)
    setFormError('')
    if (Object.keys(clientErrors).length > 0) return

    setSubmitting(true)
    try {
      await signup({ name: input.name, username: input.username, email: input.email, password: input.password })
      // 登録するとそのままログインした状態になる
      navigate('/', { replace: true })
    } catch (e) {
      // 400（入力エラー）・409（@ユーザーID・メールアドレスの重複）は項目ごとに表示する
      if (e.errors?.length) {
        setErrors(Object.fromEntries(e.errors.map(({ field, message }) => [field, message])))
      } else {
        setFormError(e.message)
      }
      setSubmitting(false)
    }
  }

  function field(name, label, input, hint) {
    return (
      <div className={styles.field}>
        <label className={styles.label} htmlFor={`signup-${name}`}>
          {label}
          {hint && <span className={styles.hint}> {hint}</span>}
        </label>
        {input}
        <p className={styles.fieldError} id={`signup-${name}-error`}>
          {errors[name] ?? ''}
        </p>
      </div>
    )
  }

  function inputProps(name) {
    return {
      id: `signup-${name}`,
      name,
      className: `${styles.input} ${errors[name] ? styles.invalid : ''}`,
      value: values[name],
      onChange: handleChange,
      'aria-invalid': Boolean(errors[name]),
      'aria-describedby': `signup-${name}-error`,
    }
  }

  return (
    <main className={styles.auth}>
      <p className={styles.logo}>RaiseTime Line</p>
      <h1 className={styles.title}>アカウント作成</h1>
      <form onSubmit={handleSubmit} noValidate>
        {field('name', '名前', <input {...inputProps('name')} autoComplete="name" />, '（1〜50文字）')}
        {field(
          'username',
          '@ユーザーID',
          <div className={styles.prefix}>
            <span className={styles.prefixMark}>@</span>
            <input {...inputProps('username')} autoComplete="username" autoCapitalize="off" />
          </div>,
          '（3〜15文字・半角英小文字・数字・_。あとからプロフィールで変更できます）',
        )}
        {field('email', 'メールアドレス', <input {...inputProps('email')} type="email" autoComplete="email" />)}
        {field(
          'password',
          'パスワード',
          <input {...inputProps('password')} type="password" autoComplete="new-password" />,
          '（8〜64文字）',
        )}
        {field(
          'passwordConfirm',
          'パスワード（確認）',
          <input {...inputProps('passwordConfirm')} type="password" autoComplete="new-password" />,
        )}
        <p className={styles.formError} role="alert">
          {formError}
        </p>
        <button type="submit" className={styles.submit} disabled={submitting}>
          {submitting ? '登録中…' : '登録する'}
        </button>
      </form>
      <p className={styles.switch}>
        すでにアカウントをお持ちの方は <Link to="/login">ログイン</Link>
      </p>
    </main>
  )
}
