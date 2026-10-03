import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { fetchComments, fetchPost } from '../api/posts.js'
import CommentForm from '../components/CommentForm/CommentForm.jsx'
import CommentItem from '../components/CommentItem/CommentItem.jsx'
import DeleteConfirmModal from '../components/DeleteConfirmModal/DeleteConfirmModal.jsx'
import Loading from '../components/Loading/Loading.jsx'
import PostCard from '../components/PostCard/PostCard.jsx'
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js'
import styles from './PostDetailPage.module.css'

// S-05 投稿詳細（docs/機能定義書/F-05_コメント.md。prototype の postDetailView と同じ並び）。
// 別の投稿の詳細へ移ったときに前の投稿の表示が残らないよう、投稿 id ごとに作り直す
export default function PostDetailPage() {
  const { postId } = useParams()
  return <PostDetail key={postId} postId={postId} />
}

function PostDetail({ postId }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [post, setPost] = useState(null)
  const [comments, setComments] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [status, setStatus] = useState('loading') // loading | ready | notFound | error
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  // 投稿と、コメントの最初の20件を同時に読み込む
  useEffect(() => {
    let cancelled = false
    Promise.all([fetchPost(postId), fetchComments(postId)])
      .then(([loadedPost, page]) => {
        if (cancelled) return
        setPost(loadedPost)
        setComments(page.items)
        setNextCursor(page.nextCursor)
        setStatus('ready')
      })
      .catch((e) => {
        if (!cancelled) setStatus(e.status === 404 ? 'notFound' : 'error')
      })
    return () => {
      cancelled = true
    }
  }, [postId, reloadKey])

  function reload() {
    setStatus('loading')
    setReloadKey((key) => key + 1)
  }

  // 前の画面へ戻る。URL を直接開いたなどで戻る先が無ければタイムラインへ
  function goBack() {
    if (location.key === 'default') {
      navigate('/')
    } else {
      navigate(-1)
    }
  }

  // ---------- コメントの無限スクロール ----------

  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    setMoreError(false)
    try {
      const page = await fetchComments(postId, nextCursor)
      setComments((current) => [...current, ...page.items.filter((c) => !current.some((x) => x.id === c.id))])
      setNextCursor(page.nextCursor)
    } catch {
      // 失敗したら自動では読み込み直さず、「再読み込み」ボタンを出す
      setMoreError(true)
    } finally {
      setLoadingMore(false)
    }
  }, [postId, nextCursor])

  const sentinelRef = useInfiniteScroll({
    enabled: status === 'ready' && nextCursor != null && !loadingMore && !moreError,
    onLoadMore: loadMore,
  })

  // ---------- 操作 ----------

  const handleUpdate = useCallback((id, changes) => {
    setPost((current) => (current && current.id === id ? { ...current, ...changes } : current))
  }, [])

  // 送信したコメントは一番新しいので、一覧の最後に来る。全件読み込み済みならその場で末尾に足す
  // （まだ続きがあるときは、スクロールで最後まで読み込むと出る）
  function handleCommentCreated(comment) {
    if (nextCursor == null) {
      setComments((current) => [...current, comment])
    }
    setPost((current) => ({ ...current, commentCount: current.commentCount + 1 }))
  }

  if (status === 'loading') return <Loading />

  if (status === 'notFound') {
    return (
      <>
        <h1 className={styles.title}>投稿</h1>
        <div className={styles.empty}>
          <p>投稿が見つかりません</p>
          <Link to="/">タイムラインへ戻る</Link>
        </div>
      </>
    )
  }

  if (status === 'error') {
    return (
      <>
        <BackButton onClick={goBack} />
        <div className={styles.empty}>
          <p>読み込みに失敗しました</p>
          <button type="button" className={styles.retry} onClick={reload}>
            再読み込み
          </button>
        </div>
      </>
    )
  }

  return (
    <>
      <BackButton onClick={goBack} />
      <PostCard post={post} detail onUpdate={handleUpdate} onDeleteRequest={setDeleting} />
      <CommentForm postId={post.id} onCreated={handleCommentCreated} />

      <section aria-label="コメント一覧">
        {comments.length === 0 && <p className={styles.empty}>まだコメントはありません</p>}
        {comments.map((comment) => (
          <CommentItem key={comment.id} comment={comment} />
        ))}
        {/* この目印が画面に近づいたら、次の20件を読み込む */}
        <div ref={sentinelRef} data-testid="comments-sentinel" />
        {loadingMore && <Loading />}
        {moreError && (
          <div className={styles.more}>
            <p className={styles.moreError}>読み込みに失敗しました</p>
            <button type="button" className={styles.retry} onClick={loadMore}>
              再読み込み
            </button>
          </div>
        )}
      </section>

      {deleting && (
        // この画面で削除したら、投稿はもう無いのでタイムラインへ戻る（docs/画面設計.md S-05）
        <DeleteConfirmModal post={deleting} onClose={() => setDeleting(null)} onDeleted={() => navigate('/', { replace: true })} />
      )}
    </>
  )
}

function BackButton({ onClick }) {
  return (
    <button type="button" className={styles.back} onClick={onClick}>
      ← 戻る
    </button>
  )
}
