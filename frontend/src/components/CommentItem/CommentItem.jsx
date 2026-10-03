import { Link } from 'react-router'
import { formatDateTime, formatRelativeTime } from '../../utils/time.js'
import Avatar from '../Avatar/Avatar.jsx'
import styles from './CommentItem.module.css'

// コメント1件（docs/画面設計.md S-05。prototype の commentHtml と同じ）。名前・@ユーザーID・日時・本文。
// アバター・名前・@ユーザーID は、コメントした人のユーザーページ（S-06）へのリンク
export default function CommentItem({ comment }) {
  const { author } = comment
  const userPath = `/users/${encodeURIComponent(author.username)}`
  return (
    <article className={styles.comment} aria-label={`${author.name}のコメント`}>
      <Link to={userPath} className={styles.avatarLink} aria-label={`${author.name}のページ`}>
        <Avatar username={author.username} name={author.name} size="small" />
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
          <time dateTime={comment.createdAt} title={formatDateTime(comment.createdAt)}>
            {formatRelativeTime(comment.createdAt)}
          </time>
        </div>
        {/* 本文は React がエスケープして表示する */}
        <p className={styles.body}>{comment.body}</p>
      </div>
    </article>
  )
}
