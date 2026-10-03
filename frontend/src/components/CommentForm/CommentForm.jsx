import { useState } from 'react'
import { createComment } from '../../api/posts.js'
import { countChars } from '../../utils/text.js'
import { isValidComment, MAX_COMMENT_LENGTH } from '../../validation/comment.js'
import styles from './CommentForm.module.css'

// 投稿詳細のコメント欄（docs/画面設計.md S-05。prototype の comment-form と同じ）。
// 空・280文字超え・送信中は「送信」を押せない。送信できたら onCreated(コメント) を呼び、入力欄を空にする
export default function CommentForm({ postId, onCreated }) {
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const length = countChars(body)
  const canSend = isValidComment(body) && !sending

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSend) return
    setSending(true)
    setError('')
    try {
      const comment = await createComment(postId, body)
      setBody('')
      onCreated(comment)
    } catch (e) {
      setError(e.message)
    } finally {
      setSending(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <textarea
        className={styles.textarea}
        rows={2}
        placeholder="コメントを書く"
        aria-label="コメント"
        value={body}
        onChange={(event) => setBody(event.target.value)}
      />
      <div className={styles.footer}>
        <span className={`${styles.counter} ${length > MAX_COMMENT_LENGTH ? styles.over : ''}`}>
          {length} / {MAX_COMMENT_LENGTH}
        </span>
        <button type="submit" className={styles.submit} disabled={!canSend}>
          {sending ? '送信中…' : '送信'}
        </button>
      </div>
      <p className={styles.error} role="alert">
        {error}
      </p>
    </form>
  )
}
