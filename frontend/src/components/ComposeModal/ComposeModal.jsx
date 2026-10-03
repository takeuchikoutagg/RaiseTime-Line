import { useEffect, useId, useRef, useState } from 'react'
import { createPost, updatePost } from '../../api/posts.js'
import { resizeForPost } from '../../utils/image.js'
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

// 編集モードで、変更があるまま閉じようとしたときの確認（prototype と同じ）
export const DISCARD_CONFIRM_MESSAGE = '変更を破棄しますか？'

// 編集モードで開いたときの画像の一覧。今付いている画像（existingId あり）から始める
function initialImages(post) {
  return (post?.images ?? []).map((image) => ({ id: `existing-${image.id}`, existingId: image.id, previewUrl: image.url }))
}

// S-04 投稿作成・編集モーダル（docs/機能定義書/F-04_投稿作成.md・F-10_投稿編集.md。prototype の openComposeModal と同じ）。
// - 作成モード（post なし）：「新しい投稿」「投稿する」。作成したら onCreated(作成した投稿)
// - 編集モード（post あり）：「投稿を編集」「保存する」。今の本文・画像が入った状態で開く。
//   何も変えていないときは保存できず、変更があるまま閉じようとしたら確認する。保存したら onSaved(編集後の投稿)
export default function ComposeModal({ post = null, onClose, onCreated, onSaved }) {
  const isEdit = post != null
  const [body, setBody] = useState(post?.body ?? '')
  // 今付いている画像は { id, existingId, previewUrl }、新しく選んだ画像は { id, file, previewUrl }
  const [images, setImages] = useState(() => initialImages(post))
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // 選んだ画像を縮めている間は投稿できない
  const [preparing, setPreparing] = useState(false)
  const fileInputId = useId()
  const nextImageId = useRef(0)
  // 閉じるときに、その時点で選んでいる画像のプレビュー URL を解放するため、最新の一覧を覚えておく
  const imagesRef = useRef(images)
  useEffect(() => {
    imagesRef.current = images
  }, [images])
  // 新しく選んだ画像のプレビュー URL だけを解放する（今付いている画像は S3 の URL なので解放しない）
  useEffect(
    () => () => imagesRef.current.filter((image) => image.file).forEach((image) => URL.revokeObjectURL(image.previewUrl)),
    [],
  )

  const length = countChars(body)
  // 編集モードで、本文か画像（外した・足した・並び）が変わっているか
  const dirty =
    isEdit &&
    (body !== post.body ||
      images.length !== post.images.length ||
      images.some((image, index) => image.existingId !== post.images[index].id))
  const canSubmit = isValidBody(body) && !submitting && !preparing && (!isEdit || dirty)

  function handleClose() {
    if (dirty && !submitting && !window.confirm(DISCARD_CONFIRM_MESSAGE)) return
    onClose()
  }

  // 選んだ画像を、送る前にブラウザの中で縮める（長い辺 1600px まで。utils/image.js）。
  // 縮めたあとの大きさで、枚数・形式・サイズを確かめる（元の写真が5MBを超えていても、縮めて収まれば送れる）
  async function handleFiles(event) {
    const files = [...event.target.files]
    // 同じファイルをもう一度選べるようにする
    event.target.value = ''
    setPreparing(true)
    let loadError = ''
    const prepared = []
    for (const file of files) {
      if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
        prepared.push(file)
        continue
      }
      try {
        prepared.push(await resizeForPost(file))
      } catch {
        // 画像として読み込めない（拡張子だけ画像の）ファイルは外す。最終的な判定はサーバーが中身で行う
        loadError ||= IMAGE_TYPE_MESSAGE
      }
    }
    setPreparing(false)
    const { accepted, error: imageError } = checkImages(prepared, imagesRef.current.length)
    setError(loadError || imageError)
    setImages((current) => [
      ...current,
      ...accepted.map((file) => ({ id: nextImageId.current++, file, previewUrl: URL.createObjectURL(file) })),
    ])
  }

  function removeImage(id, message = '') {
    setImages((current) => {
      const removed = current.find((image) => image.id === id)
      if (removed?.file) URL.revokeObjectURL(removed.previewUrl)
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
      if (isEdit) {
        const saved = await updatePost(post.id, {
          body,
          // 残す画像は今の並びのまま、追加する画像はそのあとに並ぶ（サーバーが表示順を振り直す）
          keepImageIds: images.filter((image) => image.existingId != null).map((image) => image.existingId),
          images: images.filter((image) => image.file).map((image) => image.file),
        })
        onSaved(saved)
      } else {
        const created = await createPost({ body, images: images.map((image) => image.file) })
        onCreated(created)
      }
    } catch (e) {
      // 画像の形式（中身の判定）・サイズ・S3 の失敗などは、サーバーのメッセージをそのまま出す
      setError(e.message)
      setSubmitting(false)
    }
  }

  return (
    <Modal title={isEdit ? '投稿を編集' : '新しい投稿'} onClose={handleClose}>
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
                  onError={image.file ? () => removeImage(image.id, IMAGE_TYPE_MESSAGE) : undefined}
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
            {preparing
              ? '画像を準備中…'
              : isEdit
                ? submitting
                  ? '保存中…'
                  : '保存する'
                : submitting
                  ? '投稿中…'
                  : '投稿する'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
