import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { likePost, unlikePost } from '../../api/posts.js'
import { formatDateTime, formatRelativeTime } from '../../utils/time.js'
import Avatar from '../Avatar/Avatar.jsx'
import styles from './PostCard.module.css'

// 投稿1件（docs/画面設計.md S-03 の投稿・S-05 の投稿本体。prototype の postHtml と同じ並び）。
// インプレッション数・リツイートボタンは置かない。
// - detail=false（タイムライン）：日時は「3分前」、本文を押すと投稿詳細へ
// - detail=true（投稿詳細）：日時は絶対表示、本文は大きめ、編集済みなら「編集済み 日時」も出す
// いいね数などが変わったら onUpdate(postId, 変わった項目) で親に知らせ、親が一覧の投稿を書き換える
export default function PostCard({ post, onDeleteRequest, onUpdate, detail = false }) {
  const { author, images } = post
  const detailPath = `/posts/${post.id}`
  const userPath = `/users/${encodeURIComponent(author.username)}`
  const body = <p className={styles.body}>{post.body}</p>

  return (
    <article className={`${styles.post} ${detail ? styles.detail : styles.clickable}`} aria-label={`${author.name}の投稿`}>
      {/* アバター・名前・@ユーザーID は、投稿した人のユーザーページ（S-06）へのリンク（prototype と同じ） */}
      <Link to={userPath} className={styles.avatarLink} aria-label={`${author.name}のページ`}>
        <Avatar username={author.username} name={author.name} />
      </Link>
      <div className={styles.main}>
        <div className={styles.header}>
          <Link to={userPath} className={styles.name}>
            {author.name}
          </Link>
          <Link to={userPath} className={styles.username}>
            @{author.username}
          </Link>
          <span aria-hidden="true">·</span>
          <time dateTime={post.createdAt} title={formatDateTime(post.createdAt)}>
            {detail ? formatDateTime(post.createdAt) : formatRelativeTime(post.createdAt)}
          </time>
          {post.editedAt && <span className={styles.edited}>· 編集済み</span>}
        </div>
        {/* 本文は React がエスケープして表示する（dangerouslySetInnerHTML は使わない） */}
        {detail ? (
          body
        ) : (
          <Link to={detailPath} className={styles.bodyLink}>
            {body}
          </Link>
        )}
        {images.length > 0 && (
          <div className={`${styles.images} ${styles[`images${images.length}`]}`}>
            {images.map((image) => (
              <img key={image.id} src={image.url} alt={`添付画像${image.position}`} loading="lazy" />
            ))}
          </div>
        )}
        {detail && post.editedAt && <p className={styles.meta}>編集済み {formatDateTime(post.editedAt)}</p>}
        <div className={styles.actions}>
          <LikeButton post={post} onUpdate={onUpdate} />
          {/* 読み上げソフトでも数が伝わるよう、名前に件数を入れる（「コメント 3件」） */}
          {detail ? (
            <span className={styles.action} aria-label={`コメント ${post.commentCount}件`}>
              <span aria-hidden="true">💬</span> {post.commentCount}
            </span>
          ) : (
            <Link to={detailPath} className={styles.action} aria-label={`コメント ${post.commentCount}件`}>
              <span aria-hidden="true">💬</span> {post.commentCount}
            </Link>
          )}
        </div>
      </div>
      {post.isMine && <PostMenu post={post} onDeleteRequest={onDeleteRequest} />}
    </article>
  )
}

// いいねの失敗メッセージを出しておく時間
export const LIKE_ERROR_DURATION_MS = 3000

// いいねボタン（docs/機能定義書/F-06_いいね.md）。押したらすぐ見た目と数を変え（楽観的更新）、
// API の値で合わせ直す。失敗したら元に戻してメッセージを出す（3秒で消える）。通信中にもう一度押されても受け付けない
function LikeButton({ post, onUpdate }) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const liked = post.likedByMe

  useEffect(() => {
    if (!error) return undefined
    const timer = setTimeout(() => setError(''), LIKE_ERROR_DURATION_MS)
    return () => clearTimeout(timer)
  }, [error])

  async function handleClick() {
    if (pending) return
    const before = { likedByMe: liked, likeCount: post.likeCount }
    setError('')
    setPending(true)
    onUpdate(post.id, { likedByMe: !liked, likeCount: post.likeCount + (liked ? -1 : 1) })
    try {
      const result = liked ? await unlikePost(post.id) : await likePost(post.id)
      onUpdate(post.id, { likedByMe: result.likedByMe, likeCount: result.likeCount })
    } catch (e) {
      onUpdate(post.id, before)
      setError(e.status === 404 ? '投稿が見つかりません' : 'いいねできませんでした')
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <button
        type="button"
        className={`${styles.action} ${styles.like} ${liked ? styles.liked : ''}`}
        // 読み上げソフトでも数が伝わるよう、名前に件数を入れる（「いいね 12件」）。いいね済みかは aria-pressed で伝わる
        aria-label={`いいね ${post.likeCount}件`}
        aria-pressed={liked}
        onClick={handleClick}
      >
        <span aria-hidden="true">{liked ? '♥' : '♡'}</span> {post.likeCount}
      </button>
      {error && (
        <span className={styles.actionError} role="alert">
          {error}
        </span>
      )}
    </>
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
