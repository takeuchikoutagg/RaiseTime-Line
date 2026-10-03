import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { fetchUser, fetchUserPosts } from '../api/users.js'
import Avatar from '../components/Avatar/Avatar.jsx'
import ComposeModal from '../components/ComposeModal/ComposeModal.jsx'
import DeleteConfirmModal from '../components/DeleteConfirmModal/DeleteConfirmModal.jsx'
import FollowButton from '../components/FollowButton/FollowButton.jsx'
import Loading from '../components/Loading/Loading.jsx'
import PostCard from '../components/PostCard/PostCard.jsx'
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js'
import styles from './UserPage.module.css'

// S-06 ユーザーページ（docs/機能定義書/F-08_ユーザーページ.md。prototype の userPageView と同じ並び）。
// 別のユーザーのページへ移ったときに前の人の表示が残らないよう、@ユーザーID ごとに作り直す
export default function UserPage() {
  const { username } = useParams()
  return <UserProfile key={username} username={username} />
}

function UserProfile({ username }) {
  const [user, setUser] = useState(null)
  const [posts, setPosts] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [status, setStatus] = useState('loading') // loading | ready | notFound | error
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const [editing, setEditing] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  // ユーザー情報と、投稿の最初の20件を同時に読み込む
  useEffect(() => {
    let cancelled = false
    Promise.all([fetchUser(username), fetchUserPosts(username)])
      .then(([loadedUser, page]) => {
        if (cancelled) return
        setUser(loadedUser)
        setPosts(page.items)
        setNextCursor(page.nextCursor)
        setStatus('ready')
      })
      .catch((e) => {
        if (!cancelled) setStatus(e.status === 404 ? 'notFound' : 'error')
      })
    return () => {
      cancelled = true
    }
  }, [username, reloadKey])

  function reload() {
    setStatus('loading')
    setReloadKey((key) => key + 1)
  }

  // ---------- 投稿一覧の無限スクロール ----------

  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    setMoreError(false)
    try {
      const page = await fetchUserPosts(username, nextCursor)
      setPosts((current) => [...current, ...page.items.filter((p) => !current.some((c) => c.id === p.id))])
      setNextCursor(page.nextCursor)
    } catch {
      // 失敗したら自動では読み込み直さず、「再読み込み」ボタンを出す
      setMoreError(true)
    } finally {
      setLoadingMore(false)
    }
  }, [username, nextCursor])

  const sentinelRef = useInfiniteScroll({
    enabled: status === 'ready' && nextCursor != null && !loadingMore && !moreError,
    onLoadMore: loadMore,
  })

  // ---------- 操作 ----------

  // フォロー・解除したら、ボタンとフォロワー数を API の値に合わせる
  function handleFollowChange({ followedByMe, followerCount }) {
    setUser((current) => ({ ...current, followedByMe, followerCount }))
  }

  const handleUpdate = useCallback((postId, changes) => {
    setPosts((current) => current.map((p) => (p.id === postId ? { ...p, ...changes } : p)))
  }, [])

  function handleSaved(saved) {
    setPosts((current) => current.map((p) => (p.id === saved.id ? saved : p)))
    setEditing(null)
  }

  function handleDeleted(postId) {
    setPosts((current) => current.filter((p) => p.id !== postId))
    setDeleting(null)
  }

  if (status === 'loading') return <Loading />

  if (status === 'notFound') {
    return (
      <>
        <h1 className={styles.title}>ユーザー</h1>
        <p className={styles.empty}>ユーザーが見つかりません</p>
      </>
    )
  }

  if (status === 'error') {
    return (
      <div className={styles.empty}>
        <p>読み込みに失敗しました</p>
        <button type="button" className={styles.retry} onClick={reload}>
          再読み込み
        </button>
      </div>
    )
  }

  return (
    <>
      <h1 className={styles.title}>{user.name}</h1>
      <section className={styles.profile} aria-label="プロフィール">
        <div className={styles.profileTop}>
          <Avatar username={user.username} name={user.name} size="large" />
          {/* 自分のページにはフォローボタンを出さない */}
          {!user.isMe && (
            <FollowButton username={user.username} followedByMe={user.followedByMe} onChange={handleFollowChange} />
          )}
        </div>
        <h2 className={styles.name}>{user.name}</h2>
        <div className={styles.username}>@{user.username}</div>
        <div className={styles.stats}>
          <span>
            <strong>{user.followingCount}</strong> フォロー
          </span>
          <span>
            <strong>{user.followerCount}</strong> フォロワー
          </span>
        </div>
      </section>

      {posts.length === 0 && <p className={styles.empty}>まだ投稿がありません</p>}
      {posts.map((post) => (
        <PostCard
          key={post.id}
          post={post}
          onUpdate={handleUpdate}
          onEditRequest={setEditing}
          onDeleteRequest={setDeleting}
        />
      ))}
      {/* この目印が画面に近づいたら、次の20件を読み込む */}
      <div ref={sentinelRef} data-testid="user-posts-sentinel" />
      {loadingMore && <Loading />}
      {moreError && (
        <div className={styles.more}>
          <p className={styles.moreError}>読み込みに失敗しました</p>
          <button type="button" className={styles.retry} onClick={loadMore}>
            再読み込み
          </button>
        </div>
      )}

      {editing && <ComposeModal post={editing} onClose={() => setEditing(null)} onSaved={handleSaved} />}
      {deleting && (
        <DeleteConfirmModal post={deleting} onClose={() => setDeleting(null)} onDeleted={handleDeleted} />
      )}
    </>
  )
}
