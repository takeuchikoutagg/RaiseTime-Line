import { useCallback, useEffect, useState } from 'react'
import { useOutletContext } from 'react-router'
import { checkNewPosts, fetchTimeline } from '../api/posts.js'
import { useAuth } from '../auth/useAuth.js'
import Avatar from '../components/Avatar/Avatar.jsx'
import DeleteConfirmModal from '../components/DeleteConfirmModal/DeleteConfirmModal.jsx'
import Loading from '../components/Loading/Loading.jsx'
import PostCard from '../components/PostCard/PostCard.jsx'
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js'
import styles from './TimelinePage.module.css'

// 新しい投稿があるかを確かめる間隔
export const NEW_POSTS_CHECK_INTERVAL_MS = 30_000

// S-03 タイムライン（docs/機能定義書/F-03_タイムライン.md）。自分＋フォロー中の人の投稿を新しい順に表示する。
// - 一覧の下までスクロールしたら、自動で次の20件を読み込む（無限スクロール）
// - 30秒ごとに新しい投稿があるか確かめ、あれば「新しい投稿があります」を画面上部に出す（件数は出さない）
export default function TimelinePage() {
  const { user } = useAuth()
  const { openCompose, createdPost } = useOutletContext()
  const [posts, setPosts] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [status, setStatus] = useState('loading') // loading | ready | error
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState(false)
  const [hasNew, setHasNew] = useState(false)
  const [showingNew, setShowingNew] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  // 開いた時点ですでにある createdPost（投稿詳細などから戻ってきたときの、前に作った投稿）は足さない。
  // 最初の読み込みで、サーバーから最新の一覧を取るため
  const [addedPostId, setAddedPostId] = useState(() => createdPost?.id ?? null)

  // 表示中の一番新しい投稿の id。新しい投稿があるかは、これより新しいものを探す
  const newestId = posts[0]?.id ?? 0

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

  // 投稿したら一番上へ戻り、先頭に足した自分の投稿が見えるようにする（下までスクロールしていても）
  useEffect(() => {
    if (addedPostId != null) window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [addedPostId])

  // ---------- 無限スクロール ----------

  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    setMoreError(false)
    try {
      const page = await fetchTimeline(nextCursor)
      setPosts((current) => [...current, ...page.items.filter((p) => !current.some((c) => c.id === p.id))])
      setNextCursor(page.nextCursor)
    } catch {
      // 失敗したら自動では読み込み直さず、「再読み込み」ボタンを出す（失敗が続いて何度もリクエストしないように）
      setMoreError(true)
    } finally {
      setLoadingMore(false)
    }
  }, [nextCursor])

  // 一覧の一番下の目印（sentinel）が画面に近づいたら（200px 手前）、次の20件を読み込む
  const sentinelRef = useInfiniteScroll({
    enabled: status === 'ready' && nextCursor != null && !loadingMore && !moreError,
    onLoadMore: loadMore,
  })

  // ---------- 新しい投稿のお知らせ ----------

  // 30秒ごとに確かめる。タブが裏にある間は確かめず、表に戻ったらすぐ確かめる。
  // お知らせを出したら、押されるまでは確かめるのをやめる
  useEffect(() => {
    if (status !== 'ready' || hasNew) return undefined
    let cancelled = false
    async function check() {
      if (document.visibilityState !== 'visible') return
      try {
        const result = await checkNewPosts(newestId)
        if (!cancelled && result.hasNew) setHasNew(true)
      } catch {
        // 確かめるのに失敗しても、画面には何も出さない（次の30秒後にまた確かめる）
      }
    }
    const timer = setInterval(check, NEW_POSTS_CHECK_INTERVAL_MS)
    function handleVisibilityChange() {
      if (document.visibilityState === 'visible') check()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      cancelled = true
      clearInterval(timer)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [status, hasNew, newestId])

  // 新しい投稿を先頭に足して、一番上に戻る。読み込み済みの古い投稿はそのまま残す
  async function showNewPosts() {
    setShowingNew(true)
    try {
      const page = await fetchTimeline()
      const connected = posts.length === 0 || page.items.some((p) => p.id <= newestId)
      if (connected) {
        const newer = page.items.filter((p) => p.id > newestId)
        setPosts((current) => [...newer.filter((p) => !current.some((c) => c.id === p.id)), ...current])
      } else {
        // 新しい投稿が20件より多く、今の一覧とつながらないときは、最新の20件から表示し直す
        setPosts(page.items)
        setNextCursor(page.nextCursor)
      }
      setHasNew(false)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch {
      // 失敗したらボタンを残し、もう一度押せるようにする
    } finally {
      setShowingNew(false)
    }
  }

  // いいねなどで投稿の一部が変わったら、一覧のその投稿だけ書き換える
  const handleUpdate = useCallback((postId, changes) => {
    setPosts((current) => current.map((p) => (p.id === postId ? { ...p, ...changes } : p)))
  }, [])

  function handleDeleted(postId) {
    setPosts((current) => current.filter((p) => p.id !== postId))
    setDeleting(null)
  }

  return (
    <>
      {hasNew && (
        // 画面上部に固定して、下にスクロールしていても見えるようにする
        <div className={styles.newPostsBar}>
          <button type="button" className={styles.newPostsButton} onClick={showNewPosts} disabled={showingNew}>
            {showingNew ? '読み込み中…' : '↑ 新しい投稿があります'}
          </button>
        </div>
      )}

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
            <PostCard key={post.id} post={post} onUpdate={handleUpdate} onDeleteRequest={setDeleting} />
          ))}
          {/* この目印が画面に近づいたら、次の20件を読み込む */}
          <div ref={sentinelRef} data-testid="timeline-sentinel" />
          {loadingMore && <Loading />}
          {moreError && (
            <div className={styles.more}>
              <p className={styles.moreError}>読み込みに失敗しました</p>
              <button type="button" className={styles.retry} onClick={loadMore}>
                再読み込み
              </button>
            </div>
          )}
          {nextCursor == null && posts.length > 0 && <p className={styles.end}>これ以上の投稿はありません</p>}
        </>
      )}

      {deleting && (
        <DeleteConfirmModal post={deleting} onClose={() => setDeleting(null)} onDeleted={handleDeleted} />
      )}
    </>
  )
}
