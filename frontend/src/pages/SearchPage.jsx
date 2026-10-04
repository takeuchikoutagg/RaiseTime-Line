import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { searchUsers } from '../api/users.js'
import Avatar from '../components/Avatar/Avatar.jsx'
import FollowButton from '../components/FollowButton/FollowButton.jsx'
import Loading from '../components/Loading/Loading.jsx'
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js'
import { countChars, normalizeSearchQuery } from '../utils/text.js'
import styles from './SearchPage.module.css'

// 入力が止まってから検索するまでの時間（入力のたびに API を呼ぶと負荷が高いため）
export const SEARCH_DEBOUNCE_MS = 300
const MAX_QUERY_LENGTH = 50

// S-07 ユーザー検索（docs/機能定義書/F-09_ユーザー検索.md。prototype の searchView と同じ並び）。
// キーワードは URL（?q=）にも入れるので、ユーザーページから戻ってきても同じ検索結果が出る
export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [input, setInput] = useState(() => searchParams.get('q') ?? '')
  // 入力が止まってから決まった、実際に検索するキーワード
  const [query, setQuery] = useState(() => normalizeSearchQuery(searchParams.get('q') ?? ''))
  // どのキーワードの結果か（query）も一緒に持つ。キーワードが変わった直後は、前のキーワードの結果を出さない
  const [result, setResult] = useState({ query: null, items: [], nextOffset: null, error: false })
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState(false)

  const tooLong = countChars(query) > MAX_QUERY_LENGTH
  const searchable = query !== '' && !tooLong
  // idle（検索しない）| loading | ready | error は、キーワードと結果から決まる
  const status = !searchable ? 'idle' : result.query !== query ? 'loading' : result.error ? 'error' : 'ready'
  const results = status === 'ready' ? result.items : []
  const nextOffset = status === 'ready' ? result.nextOffset : null

  // 入力が300ミリ秒止まったら、検索するキーワードを決め、URL にも入れる（履歴は増やさない）
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(normalizeSearchQuery(input))
      setSearchParams(input ? { q: input } : {}, { replace: true })
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [input, setSearchParams])

  // キーワードが変わったら最初の20件を検索する。古いキーワードの結果があとから届いても使わない
  useEffect(() => {
    if (!searchable) return undefined
    let cancelled = false
    searchUsers(query)
      .then((page) => {
        if (!cancelled) setResult({ query, items: page.items, nextOffset: page.nextOffset, error: false })
      })
      .catch(() => {
        if (!cancelled) setResult({ query, items: [], nextOffset: null, error: true })
      })
    return () => {
      cancelled = true
    }
  }, [query, searchable])

  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    setMoreError(false)
    try {
      const page = await searchUsers(query, nextOffset)
      setResult((current) =>
        current.query !== query
          ? current
          : {
              ...current,
              items: [...current.items, ...page.items.filter((u) => !current.items.some((c) => c.username === u.username))],
              nextOffset: page.nextOffset,
            },
      )
    } catch {
      setMoreError(true)
    } finally {
      setLoadingMore(false)
    }
  }, [query, nextOffset])

  const sentinelRef = useInfiniteScroll({
    enabled: status === 'ready' && nextOffset != null && !loadingMore && !moreError,
    onLoadMore: loadMore,
  })

  function handleFollowChange(username, { followedByMe }) {
    setResult((current) => ({
      ...current,
      items: current.items.map((u) => (u.username === username ? { ...u, followedByMe } : u)),
    }))
  }

  return (
    <>
      <h1 className={styles.title}>ユーザー検索</h1>
      <div className={styles.searchBox}>
        <input
          type="search"
          className={styles.input}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="🔍 @ユーザーID または 名前で検索"
          aria-label="ユーザー検索"
          autoComplete="off"
          autoCapitalize="off"
        />
      </div>

      {!query && <p className={styles.empty}>@ユーザーID または名前で、フォローしたいユーザーを探せます</p>}
      {tooLong && <p className={styles.empty}>キーワードは50文字以内で入力してください</p>}
      {status === 'loading' && <Loading />}
      {status === 'error' && <p className={styles.empty}>検索に失敗しました。時間をおいてもう一度お試しください</p>}
      {status === 'ready' && results.length === 0 && <p className={styles.empty}>該当するユーザーが見つかりません</p>}
      {status === 'ready' && (
        <ul className={styles.list} aria-label="検索結果">
          {results.map((user) => (
            <UserRow key={user.username} user={user} onFollowChange={(result) => handleFollowChange(user.username, result)} />
          ))}
        </ul>
      )}
      {/* この目印が画面に近づいたら、次の20件を読み込む */}
      <div ref={sentinelRef} data-testid="search-sentinel" />
      {loadingMore && <Loading />}
      {moreError && (
        <div className={styles.more}>
          <p className={styles.moreError}>読み込みに失敗しました</p>
          <button type="button" className={styles.retry} onClick={loadMore}>
            再読み込み
          </button>
        </div>
      )}
    </>
  )
}

// 検索結果の1行（prototype の user-row）。自分の行はフォローボタンの代わりに「あなた」
function UserRow({ user, onFollowChange }) {
  const userPath = `/users/${encodeURIComponent(user.username)}`
  return (
    <li className={styles.row}>
      <Link to={userPath} className={styles.avatarLink} aria-label={`${user.name}のページ`}>
        <Avatar username={user.username} name={user.name} avatarUrl={user.avatarUrl} />
      </Link>
      <div className={styles.info}>
        <Link to={userPath} className={styles.name}>
          {user.name}
        </Link>
        <Link to={userPath} className={styles.username}>
          @{user.username}
        </Link>
      </div>
      {user.isMe ? (
        <span className={styles.me}>あなた</span>
      ) : (
        <FollowButton username={user.username} followedByMe={user.followedByMe} onChange={onFollowChange} />
      )}
    </li>
  )
}
