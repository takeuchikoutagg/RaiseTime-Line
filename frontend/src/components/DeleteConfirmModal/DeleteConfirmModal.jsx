import { useState } from 'react'
import { deletePost } from '../../api/posts.js'
import Modal from '../Modal/Modal.jsx'
import styles from './DeleteConfirmModal.module.css'

// S-08 投稿削除の確認（docs/機能定義書/F-11_投稿削除.md）
export default function DeleteConfirmModal({ post, onClose, onDeleted }) {
  const [error, setError] = useState('')
  const [deleting, setDeleting] = useState(false)

  async function handleDelete() {
    setDeleting(true)
    setError('')
    try {
      await deletePost(post.id)
      onDeleted(post.id)
    } catch (e) {
      // すでに削除されていた（404）なら、画面からも消す
      if (e.status === 404) {
        onDeleted(post.id)
        return
      }
      setError(e.message)
      setDeleting(false)
    }
  }

  return (
    <Modal title="この投稿を削除しますか？" onClose={onClose} variant="alert">
      <p className={styles.text}>
        削除すると元に戻せません。
        <br />
        付いているコメントといいねも削除されます。
      </p>
      <p className={styles.error} role="alert">
        {error}
      </p>
      <div className={styles.actions}>
        <button type="button" className={styles.cancel} onClick={onClose}>
          キャンセル
        </button>
        <button type="button" className={styles.delete} onClick={handleDelete} disabled={deleting}>
          {deleting ? '削除中…' : '削除する'}
        </button>
      </div>
    </Modal>
  )
}
