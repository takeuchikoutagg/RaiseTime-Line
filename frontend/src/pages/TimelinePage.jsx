import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router'
import { fetchTimeline } from '../api/posts.js'
import { useAuth } from '../auth/useAuth.js'
import Avatar from '../components/Avatar/Avatar.jsx'
import DeleteConfirmModal from '../components/DeleteConfirmModal/DeleteConfirmModal.jsx'
import Loading from '../components/Loading/Loading.jsx'
import PostCard from '../components/PostCard/PostCard.jsx'
import styles from './TimelinePage.module.css'

// S-03 タイムライン（docs/機能定義書/F-03_タイムライン.md）。自分＋フォロー中の人の投稿を新しい順に20件ずつ
export default function TimelinePage() {
  const { user } = useAuth()
  const { openCompose, createdPost } = useOutletContext()
  const [posts, setPosts] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState('')
  const [deleting, setDeleting] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [addedPostId, setAddedPostId] = useState(null)

  // 最初の20件を読み込む。「再読み込み」で reloadKey を変えると、もう一度読み込む
  useEffect(() => {
    let cancelled = false
    fetchTimeline()
      .then((page) => {
        if (cancelled) return
        setPosts(page.items)
        setNextCursor(page.nextCursor)
        setStatus('ready')
      })
      .catch(() => {
        if (!cancelled) setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const reload = useCallback(() => {
    setStatus('loading')
    setReloadKey((key) => key + 1)
  }, [])

  // 投稿モーダルで作った投稿を、読み込み直さずに先頭に足す。
  // 「前回足した投稿」と比べて、新しい投稿が来たときだけ足す（React の「表示中に state を調整する」書き方）
  if (createdPost && createdPost.id !== addedPostId) {
    setAddedPostId(createdPost.id)
    setPosts((current) => (current.some((p) => p.id === createdPost.id) ? current : [createdPost, ...current]))
  }

  async function loadMore() {
    setLoadingMore(true)
    setMoreError('')
    try {
      const page = await fetchTimeline(nextCursor)
      setPosts((current) => [...current, ...page.items])
      setNextCursor(page.nextCursor)
    } catch {
      setMoreError('読み込みに失敗しました')
    } finally {
      setLoadingMore(false)
    }
  }

  function handleDeleted(postId) {
    setPosts((current) => current.filter((p) => p.id !== postId))
    setDeleting(null)
  }

  return (
    <>
      <h1 className={styles.title}>ホーム</h1>
      <div className={styles.composeBar}>
        <Avatar username={user.username} name={user.name} />
        <button type="button" className={styles.composeButton} onClick={openCompose}>
          いまどうしてる？
        </button>
      </div>

      {status === 'loading' && <Loading />}
      {status === 'error' && (
        <div className={styles.empty}>
          <p>読み込みに失敗しました</p>
          <button type="button" className={styles.retry} onClick={reload}>
            再読み込み
          </button>
        </div>
      )}
      {status === 'ready' && posts.length === 0 && (
        // ユーザー検索ができたら「ユーザーを検索してフォローすると…」とリンクを出す（docs/画面設計.md S-03）
        <p className={styles.empty}>まだ投稿がありません。最初の投稿をしてみましょう</p>
      )}
      {status === 'ready' && (
        <>
          {posts.map((post) => (
            <PostCard key={post.id} post={post} onDeleteRequest={setDeleting} />
          ))}
          {nextCursor != null && (
            <div className={styles.more}>
              <button type="button" className={styles.moreButton} onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? '読み込み中…' : 'もっと見る'}
              </button>
              {moreError && <p className={styles.moreError}>{moreError}</p>}
            </div>
          )}
        </>
      )}

      {deleting && (
        <DeleteConfirmModal post={deleting} onClose={() => setDeleting(null)} onDeleted={handleDeleted} />
      )}
    </>
  )
}
