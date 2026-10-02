import { useEffect, useRef, useState } from 'react'
import { formatDateTime, formatRelativeTime } from '../../utils/time.js'
import Avatar from '../Avatar/Avatar.jsx'
import styles from './PostCard.module.css'

// 投稿1件（docs/画面設計.md S-03 の投稿）。インプレッション数・リツイートボタンは置かない。
// いいね・コメント数は、その機能ができてから足す
export default function PostCard({ post, onDeleteRequest }) {
  const { author, images } = post
  return (
    <article className={styles.post} aria-label={`${author.name}の投稿`}>
      <Avatar username={author.username} name={author.name} />
      <div className={styles.main}>
        <div className={styles.header}>
          <span className={styles.name}>{author.name}</span>
          <span className={styles.username}>@{author.username}</span>
          <span aria-hidden="true">·</span>
          <time dateTime={post.createdAt} title={formatDateTime(post.createdAt)}>
            {formatRelativeTime(post.createdAt)}
          </time>
          {post.editedAt && <span className={styles.edited}>· 編集済み</span>}
        </div>
        {/* 本文は React がエスケープして表示する（dangerouslySetInnerHTML は使わない） */}
        <p className={styles.body}>{post.body}</p>
        {images.length > 0 && (
          <div className={`${styles.images} ${styles[`images${images.length}`]}`}>
            {images.map((image) => (
              <img key={image.id} src={image.url} alt={`添付画像${image.position}`} loading="lazy" />
            ))}
          </div>
        )}
      </div>
      {post.isMine && <PostMenu post={post} onDeleteRequest={onDeleteRequest} />}
    </article>
  )
}

// 自分の投稿にだけ出す「…」メニュー。表示を切り替えるだけで、削除できるかは API が本人か確かめる
function PostMenu({ post, onDeleteRequest }) {
  const [open, setOpen] = useState(false)
  const menuRef = useRef(null)

  // メニューの外をクリックしたら閉じる
  useEffect(() => {
    if (!open) return undefined
    function handleClick(event) {
      if (!menuRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div className={styles.menu} ref={menuRef}>
      <button
        type="button"
        className={styles.menuButton}
        aria-label="メニュー"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        …
      </button>
      {open && (
        <div className={styles.menuList} role="menu">
          <button
            type="button"
            role="menuitem"
            className={styles.danger}
            onClick={() => {
              setOpen(false)
              onDeleteRequest(post)
            }}
          >
            削除
          </button>
        </div>
      )}
    </div>
  )
}
