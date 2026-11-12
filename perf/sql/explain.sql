-- 主要な SQL の実行計画と実行時間（docs/パフォーマンステスト.md 5章）。
-- 実行：perf/run.sh explain（perf/run.sh seed で大量データを入れた raisetime_perf に対して）
--
-- 各 SQL は backend/src/main/resources/mapper/ の SQL を写したもの。見出しのコメントに元の mapper の id を書いている。
-- mapper の SQL を変えたら、ここも合わせる。
-- 見るところ：Execution Time（実行時間）と、大きなテーブルが Seq Scan（全件を順に読む）になっていないか

\set ON_ERROR_STOP on
\pset pager off

-- 調べる人と投稿を決める（id は seed.sql のとおり）
SELECT id AS viewer_id FROM users WHERE username = 'perf_00001' \gset
SELECT id AS heavy_id FROM users WHERE username = 'perf_heavy1' \gset
SELECT id AS newbie_id FROM users WHERE username = 'perf_newbie' \gset
SELECT id AS few_id FROM users WHERE username = 'perf_few' \gset
SELECT max(id) - 1000 AS cursor_id FROM posts \gset
SELECT max(id) - 50 AS newer_after FROM posts \gset

\echo
\echo '===== 1. タイムライン 1ページ目（PostMapper.findTimeline・通常のユーザー：100人をフォロー） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :viewer_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE (p.user_id = :viewer_id
       OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewer_id))
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 2. タイムライン 1ページ目（PostMapper.findTimeline・2000人をフォローする人） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :heavy_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE (p.user_id = :heavy_id
       OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :heavy_id))
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 2a. タイムライン 1ページ目（PostMapper.findTimeline・3人だけフォローする人） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :few_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE (p.user_id = :few_id
       OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :few_id))
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 2b. タイムライン 1ページ目（PostMapper.findTimeline・始めたばかりの人：投稿もフォローもなし） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :newbie_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE (p.user_id = :newbie_id
       OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :newbie_id))
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 3. タイムラインの続き（PostMapper.findTimeline・before あり・3人だけフォローする人） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :few_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE (p.user_id = :few_id
       OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :few_id))
  AND p.id < :cursor_id
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 4. 新しい投稿があるか（PostMapper.existsTimelineNewerThan・始めたばかりの人：該当なし） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT EXISTS (
    SELECT 1
    FROM posts p
    WHERE (p.user_id = :newbie_id
           OR p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :newbie_id))
      AND p.id > :newer_after
);

\echo
\echo '===== 5. ユーザーページの投稿（PostMapper.findByUser） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :viewer_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE p.user_id = 2
ORDER BY p.id DESC
LIMIT 21;

\echo
\echo '===== 6. 投稿の詳細（PostMapper.findDetailById） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.user_id, p.body, p.edited_at, p.created_at, p.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (SELECT count(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = :viewer_id) AS liked_by_me
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE p.id = 1;

\echo
\echo '===== 7. コメント一覧（CommentMapper.findByPostId：コメント10件の投稿） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id, c.post_id, c.user_id, c.body, c.created_at, c.updated_at,
       u.username AS author_username, u.name AS author_name, u.avatar_key AS author_avatar_key
FROM comments c
JOIN users u ON u.id = c.user_id
WHERE c.post_id = 1
ORDER BY c.id
LIMIT 21;

\echo
\echo '===== 8. ユーザー検索・部分一致（UserMapper.search：「taro」で2000人が当たる） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.id, u.username, u.name, u.avatar_key,
       EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :viewer_id AND f.followee_id = u.id) AS followed_by_me
FROM users u
WHERE u.username ILIKE '%' || 'taro' || '%' ESCAPE '\'
   OR u.name ILIKE '%' || 'taro' || '%' ESCAPE '\'
ORDER BY
    CASE
        WHEN u.username = 'taro' THEN 0
        WHEN u.username LIKE 'taro' || '%' ESCAPE '\' THEN 1
        ELSE 2
    END,
    u.username COLLATE "C"
LIMIT 21 OFFSET 0;

\echo
\echo '===== 9. ユーザー検索・前方一致（UserMapper.search：「perf_0001」で10人が当たる） ====='
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.id, u.username, u.name, u.avatar_key,
       EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :viewer_id AND f.followee_id = u.id) AS followed_by_me
FROM users u
WHERE u.username ILIKE '%' || 'perf\_0001' || '%' ESCAPE '\'
   OR u.name ILIKE '%' || 'perf\_0001' || '%' ESCAPE '\'
ORDER BY
    CASE
        WHEN u.username = 'perf_0001' THEN 0
        WHEN u.username LIKE 'perf\_0001' || '%' ESCAPE '\' THEN 1
        ELSE 2
    END,
    u.username COLLATE "C"
LIMIT 21 OFFSET 0;
