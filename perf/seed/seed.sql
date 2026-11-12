-- 性能テスト用の大量データ（docs/パフォーマンステスト.md 3章）。
-- 実行：perf/run.sh seed（性能テスト用の DB raisetime_perf にだけ入れる。開発用の raisetime には入れない）
-- テーブルは Flyway が作ったもの（backend/src/main/resources/db/migration/）をそのまま使う。
-- 何度流しても同じ状態になるよう、最初にすべてのデータを消してから入れ直す。

\set ON_ERROR_STOP on
\timing on

-- 開発用の DB を誤って消さないよう、raisetime_perf 以外では止める
DO $$
BEGIN
    IF current_database() <> 'raisetime_perf' THEN
        RAISE EXCEPTION '性能テスト用の DB（raisetime_perf）ではありません：%', current_database();
    END IF;
END
$$;

-- パスワードの BCrypt ハッシュを作るため（性能テスト用の DB にだけ入れる）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- users を消すと、投稿・いいね・コメント・フォロー・リフレッシュトークンも外部キーでまとめて消える。
-- id も 1 から振り直す（下のフォロー・いいねは、ユーザーと投稿の id が 1 から続いていることを前提にしている）
TRUNCATE users RESTART IDENTITY CASCADE;
TRUNCATE posts, post_images, comments, likes, follows, refresh_tokens RESTART IDENTITY;

-- ---------- ユーザー：1万人（id 1〜10000）と、たくさんフォローする人5人（id 10001〜10005） ----------
-- パスワードは全員 password123。BCrypt は1回の計算が重いので、ハッシュは1回だけ作って全員に使う。
-- pgcrypto の bf は $2a$ 形式で、Spring Security の BCryptPasswordEncoder で照合できる。
-- 名前は検索のテスト用に5種類の言葉を混ぜる（「taro」で探すと2000人が見つかる）
INSERT INTO users (username, name, email, password_hash, created_at, updated_at)
SELECT 'perf_' || lpad(i::text, 5, '0'),
       (ARRAY['taro', 'hanako', 'ichiro', 'sakura', 'kenta'])[i % 5 + 1] || ' ' || i,
       'perf_' || lpad(i::text, 5, '0') || '@example.com',
       h.hash,
       now() - interval '400 days',
       now() - interval '400 days'
FROM generate_series(1, 10000) AS i,
     (SELECT crypt('password123', gen_salt('bf', 10)) AS hash) AS h;

INSERT INTO users (username, name, email, password_hash, created_at, updated_at)
SELECT 'perf_heavy' || i,
       'たくさんフォローする人 ' || i,
       'perf_heavy' || i || '@example.com',
       (SELECT password_hash FROM users WHERE id = 1),
       now() - interval '400 days',
       now() - interval '400 days'
FROM generate_series(1, 5) AS i;

-- 始めたばかりの人（id 10006）と、3人だけフォローしている人（id 10007）。
-- タイムラインは「新しい順に投稿を読み、自分かフォロー中の人のものを21件集める」ので、該当する投稿が少ない人ほど、
-- たくさんの投稿を読むことになる。たくさんフォローする人より、こちらが本当の最悪ケース
INSERT INTO users (username, name, email, password_hash, created_at, updated_at)
VALUES ('perf_newbie', '始めたばかりの人', 'perf_newbie@example.com', (SELECT password_hash FROM users WHERE id = 1), now(), now()),
       ('perf_few', '3人だけフォローする人', 'perf_few@example.com', (SELECT password_hash FROM users WHERE id = 1), now(), now());

-- ---------- フォロー ----------
-- 通常のユーザーは100人をフォローする。相手は自分の id から 97 ずつずらした人（97×100 < 10000 なので、自分にも重複にもならない）
INSERT INTO follows (follower_id, followee_id)
SELECT u, (u - 1 + k * 97) % 10000 + 1
FROM generate_series(1, 10000) AS u,
     generate_series(1, 100) AS k;

-- タイムラインの最悪ケース：2000人をフォローする人（id が 5 の倍数の人を全員）
INSERT INTO follows (follower_id, followee_id)
SELECT 10000 + h, k * 5
FROM generate_series(1, 5) AS h,
     generate_series(1, 2000) AS k;

-- 3人だけフォローしている人（perf_few）
INSERT INTO follows (follower_id, followee_id)
SELECT 10007, k FROM generate_series(1, 3) AS k;

-- ---------- 投稿：50万件（1人50件） ----------
-- id の順と作成日時の順をそろえる（タイムラインは id の新しい順に並べるため）。1分おきに約1年分
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT i % 10000 + 1,
       '性能テストの投稿 ' || i || ' ' || repeat('あ', i % 100),
       now() - (500000 - i) * interval '1 minute',
       now() - (500000 - i) * interval '1 minute'
FROM generate_series(1, 500000) AS i;

-- ---------- いいね：100万件（1人100件） ----------
-- ユーザー u が、投稿 (u×53 + j×4999) をいいねする。j×4999 は j < 100 で 500000 未満なので、同じ人が同じ投稿を2回いいねしない
INSERT INTO likes (post_id, user_id)
SELECT ((u * 53 + j * 4999) % 500000) + 1, u
FROM generate_series(1, 10000) AS u,
     generate_series(0, 99) AS j;

-- ---------- コメント：20万件 ----------
-- 2万件の投稿に10件ずつ（コメント一覧の読み込みを確かめるため、ある程度まとめる）
INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
SELECT (i % 20000) * 25 + 1,
       i % 10000 + 1,
       '性能テストのコメント ' || i,
       now() - (200000 - i) * interval '2 minutes',
       now() - (200000 - i) * interval '2 minutes'
FROM generate_series(1, 200000) AS i;

-- 統計情報を作り直す（入れた直後は、PostgreSQL が件数を知らず、遅い実行計画を選ぶことがあるため）
ANALYZE;

-- 入った件数を表示する
SELECT 'users' AS テーブル, count(*) AS 件数 FROM users
UNION ALL SELECT 'follows', count(*) FROM follows
UNION ALL SELECT 'posts', count(*) FROM posts
UNION ALL SELECT 'likes', count(*) FROM likes
UNION ALL SELECT 'comments', count(*) FROM comments;
