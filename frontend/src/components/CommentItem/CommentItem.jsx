import { formatDateTime, formatRelativeTime } from '../../utils/time.js'
import Avatar from '../Avatar/Avatar.jsx'
import styles from './CommentItem.module.css'

// コメント1件（docs/画面設計.md S-05。prototype の commentHtml と同じ）。名前・@ユーザーID・日時・本文。
// 名前は、ユーザーページ（S-06）ができたらリンクにする
export default function CommentItem({ comment }) {
  const { author } = comment
  return (
    <article className={styles.comment} aria-label={`${author.name}のコメント`}>
      <Avatar username={author.username} name={author.name} size="small" />
      <div className={styles.main}>
        <div className={styles.header}>
          <span className={styles.name}>{author.name}</span>
          <span>@{author.username}</span>
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
