import { useEffect, useId, useRef, useState } from 'react'
import { updateProfile } from '../../api/users.js'
import { resizeForAvatar } from '../../utils/image.js'
import { countChars } from '../../utils/text.js'
import { ACCEPTED_IMAGE_TYPES, IMAGE_TYPE_MESSAGE } from '../../validation/post.js'
import { MAX_BIO_LENGTH, validateProfile } from '../../validation/profile.js'
import Avatar from '../Avatar/Avatar.jsx'
import { DISCARD_CONFIRM_MESSAGE } from '../ComposeModal/ComposeModal.jsx'
import Modal from '../Modal/Modal.jsx'
import styles from './ProfileEditModal.module.css'

// S-09 プロフィール編集（docs/機能定義書/F-12_プロフィール編集.md）。
// 表示名・@ユーザーID・自己紹介文・プロフィール画像を変える。見た目は投稿モーダルと新規登録の入力欄に合わせている。
// 何も変えていなければ保存できず、変更があるまま閉じようとしたら確認する（投稿の編集と同じ）。保存したら onSaved(編集後のユーザー情報)
export default function ProfileEditModal({ profile, onClose, onSaved }) {
  const [values, setValues] = useState({ name: profile.name, username: profile.username, bio: profile.bio ?? '' })
  // 新しく選んだ画像 { file, previewUrl }。null なら今の画像のまま（または外したまま）
  const [newAvatar, setNewAvatar] = useState(null)
  const [removeAvatar, setRemoveAvatar] = useState(false)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  // 選んだ画像を縮めている間は保存できない
  const [preparing, setPreparing] = useState(false)
  // 「画像を変更」ボタンから開く、ファイル選択欄（画面には出さない）
  const fileInputRef = useRef(null)
  const idPrefix = useId()

  // 閉じるときに、選んだ画像のプレビュー URL を解放する
  const newAvatarRef = useRef(newAvatar)
  useEffect(() => {
    newAvatarRef.current = newAvatar
  }, [newAvatar])
  useEffect(() => () => newAvatarRef.current && URL.revokeObjectURL(newAvatarRef.current.previewUrl), [])

  const avatarUrl = newAvatar ? newAvatar.previewUrl : removeAvatar ? null : profile.avatarUrl
  const dirty =
    values.name !== profile.name ||
    values.username !== profile.username ||
    values.bio !== (profile.bio ?? '') ||
    newAvatar != null ||
    removeAvatar
  const bioLength = countChars(values.bio)

  function handleChange(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
  }

  // 選んだ画像は、送る前に真ん中を正方形に切り抜いて 400×400px に縮める（utils/image.js）。
  // アイコンは小さく表示するだけなので、元の写真をそのまま送らない（サーバーの上限は2MB）
  async function handleFile(event) {
    const file = event.target.files[0]
    event.target.value = ''
    if (!file) return
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      setErrors((current) => ({ ...current, avatar: IMAGE_TYPE_MESSAGE }))
      return
    }
    setPreparing(true)
    try {
      const resized = await resizeForAvatar(file)
      if (newAvatar) URL.revokeObjectURL(newAvatar.previewUrl)
      setNewAvatar({ file: resized, previewUrl: URL.createObjectURL(resized) })
      setRemoveAvatar(false)
      setErrors((current) => ({ ...current, avatar: undefined }))
    } catch {
      // 画像として読み込めない（拡張子だけ画像の）ファイル
      setErrors((current) => ({ ...current, avatar: IMAGE_TYPE_MESSAGE }))
    } finally {
      setPreparing(false)
    }
  }

  function handleRemoveAvatar() {
    if (newAvatar) URL.revokeObjectURL(newAvatar.previewUrl)
    setNewAvatar(null)
    setRemoveAvatar(true)
    setErrors((current) => ({ ...current, avatar: undefined }))
  }

  function handleClose() {
    if (dirty && !saving && !window.confirm(DISCARD_CONFIRM_MESSAGE)) return
    onClose()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const found = validateProfile(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    setFormError('')
    try {
      const saved = await updateProfile({
        name: values.name.trim(),
        username: values.username,
        bio: values.bio,
        avatar: newAvatar?.file ?? null,
        removeAvatar: removeAvatar && !newAvatar,
      })
      onSaved(saved)
    } catch (e) {
      // @ユーザーID の重複（409）・入力エラー（400）は項目の下に、それ以外はフォームの下に出す
      if (e.errors?.length > 0) {
        setErrors(Object.fromEntries(e.errors.map((error) => [error.field, error.message])))
      } else {
        setFormError(e.message)
      }
      setSaving(false)
    }
  }

  function fieldProps(name) {
    return {
      id: `${idPrefix}-${name}`,
      name,
      value: values[name],
      onChange: handleChange,
      'aria-invalid': Boolean(errors[name]),
      'aria-describedby': `${idPrefix}-${name}-error`,
    }
  }

  function fieldError(name) {
    return (
      <p id={`${idPrefix}-${name}-error`} className={styles.fieldError}>
        {errors[name]}
      </p>
    )
  }

  return (
    <Modal title="プロフィールを編集" onClose={handleClose}>
      <form onSubmit={handleSubmit} className={styles.form} noValidate>
        <div className={styles.avatarRow}>
          <Avatar
            username={values.username || profile.username}
            name={values.name || profile.name}
            avatarUrl={avatarUrl}
            size="large"
          />
          <div className={styles.avatarButtons}>
            {/* label ではなくボタンにする（label は Tab で止まらず、キーボードだけでは画像を選べないため） */}
            <button type="button" className={styles.secondary} onClick={() => fileInputRef.current?.click()}>
              画像を変更
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPTED_IMAGE_TYPES.join(',')}
              hidden
              onChange={handleFile}
              data-testid="avatar-input"
            />
            {avatarUrl && (
              <button type="button" className={styles.secondary} onClick={handleRemoveAvatar}>
                画像を外す
              </button>
            )}
          </div>
        </div>
        {fieldError('avatar')}

        <label className={styles.label} htmlFor={`${idPrefix}-name`}>
          名前 <span className={styles.hint}>（1〜50文字）</span>
        </label>
        <input {...fieldProps('name')} className={`${styles.input} ${errors.name ? styles.invalid : ''}`} />
        {fieldError('name')}

        <label className={styles.label} htmlFor={`${idPrefix}-username`}>
          @ユーザーID <span className={styles.hint}>（3〜15文字・半角英小文字・数字・_）</span>
        </label>
        <div className={styles.prefix}>
          <span className={styles.prefixMark}>@</span>
          <input
            {...fieldProps('username')}
            className={`${styles.input} ${errors.username ? styles.invalid : ''}`}
            autoCapitalize="off"
          />
        </div>
        {fieldError('username')}
        {values.username !== profile.username && !errors.username && (
          <p className={styles.note}>@ユーザーIDを変えると、今のページの URL は使えなくなります</p>
        )}

        <label className={styles.label} htmlFor={`${idPrefix}-bio`}>
          自己紹介
        </label>
        <textarea
          {...fieldProps('bio')}
          rows={3}
          className={`${styles.input} ${styles.textarea} ${errors.bio ? styles.invalid : ''}`}
        />
        <div className={styles.bioFooter}>
          {fieldError('bio')}
          <span className={`${styles.counter} ${bioLength > MAX_BIO_LENGTH ? styles.over : ''}`}>
            {bioLength} / {MAX_BIO_LENGTH}
          </span>
        </div>

        <p className={styles.formError} role="alert">
          {formError}
        </p>
        <div className={styles.actions}>
          <button type="submit" className={styles.submit} disabled={!dirty || saving || preparing}>
            {preparing ? '画像を準備中…' : saving ? '保存中…' : '保存する'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
