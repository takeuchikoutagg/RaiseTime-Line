import { useEffect, useId, useRef, useState } from 'react'
import { createPost } from '../../api/posts.js'
import { countChars } from '../../utils/text.js'
import {
  ACCEPTED_IMAGE_TYPES,
  BODY_MESSAGE,
  IMAGE_TYPE_MESSAGE,
  MAX_BODY_LENGTH,
  MAX_IMAGES,
  checkImages,
  isValidBody,
} from '../../validation/post.js'
import Modal from '../Modal/Modal.jsx'
import styles from './ComposeModal.module.css'

// S-04 投稿作成モーダル（docs/機能定義書/F-04_投稿作成.md）
export default function ComposeModal({ onClose, onCreated }) {
  const [body, setBody] = useState('')
  // { id, file, previewUrl }
  const [images, setImages] = useState([])
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const fileInputId = useId()
  const nextImageId = useRef(0)
  // 閉じるときに、その時点で選んでいる画像のプレビュー URL を解放するため、最新の一覧を覚えておく
  const imagesRef = useRef(images)
  useEffect(() => {
    imagesRef.current = images
  }, [images])
  useEffect(() => () => imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl)), [])

  const length = countChars(body)
  const canSubmit = isValidBody(body) && !submitting

  function handleFiles(event) {
    const { accepted, error: imageError } = checkImages([...event.target.files], images.length)
    setError(imageError)
    setImages((current) => [
      ...current,
      ...accepted.map((file) => ({ id: nextImageId.current++, file, previewUrl: URL.createObjectURL(file) })),
    ])
    // 同じファイルをもう一度選べるようにする
    event.target.value = ''
  }

  function removeImage(id, message = '') {
    setImages((current) => {
      const removed = current.find((image) => image.id === id)
      if (removed) URL.revokeObjectURL(removed.previewUrl)
      return current.filter((image) => image.id !== id)
    })
    setError(message)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!isValidBody(body)) {
      setError(BODY_MESSAGE)
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const post = await createPost({ body, images: images.map((image) => image.file) })
      onCreated(post)
    } catch (e) {
      // 画像の形式（中身の判定）・サイズ・S3 の失敗などは、サーバーのメッセージをそのまま出す
      setError(e.message)
      setSubmitting(false)
    }
  }

  return (
    <Modal title="新しい投稿" onClose={onClose}>
      <form onSubmit={handleSubmit} className={styles.form} noValidate>
        <textarea
          className={styles.textarea}
          rows={5}
          placeholder="いまどうしてる？"
          aria-label="本文"
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        {images.length > 0 && (
          <ul className={styles.previews} aria-label="選んだ画像">
            {images.map((image, index) => (
              <li key={image.id} className={styles.preview}>
                {/* ブラウザが画像として表示できない（拡張子だけ画像の）ファイルは、送る前に外す。最終的な判定はサーバーが中身で行う */}
                <img
                  src={image.previewUrl}
                  alt={`選んだ画像${index + 1}`}
                  onError={() => removeImage(image.id, IMAGE_TYPE_MESSAGE)}
                />
                <button
                  type="button"
                  className={styles.removeImage}
                  onClick={() => removeImage(image.id)}
                  aria-label={`画像${index + 1}を外す`}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className={styles.toolbar}>
          <label
            htmlFor={fileInputId}
            className={`${styles.addImage} ${images.length >= MAX_IMAGES ? styles.disabled : ''}`}
          >
            画像を追加（{images.length}/{MAX_IMAGES}）
          </label>
          <input
            id={fileInputId}
            type="file"
            accept={ACCEPTED_IMAGE_TYPES.join(',')}
            multiple
            hidden
            disabled={images.length >= MAX_IMAGES}
            onChange={handleFiles}
            data-testid="image-input"
          />
          <span className={`${styles.counter} ${length > MAX_BODY_LENGTH ? styles.over : ''}`} aria-live="polite">
            {length} / {MAX_BODY_LENGTH}
          </span>
        </div>
        <p className={styles.error} role="alert">
          {error}
        </p>
        <div className={styles.actions}>
          <button type="submit" className={styles.submit} disabled={!canSubmit}>
            {submitting ? '投稿中…' : '投稿する'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
