-- ユーザー検索（F-09）の部分一致（ILIKE '%taro%'）を速くするためのインデックス。
-- 通常の B-tree インデックスは前後に % の付いた部分一致には効かないため、pg_trgm（3文字ずつに区切って索引を作る拡張）の GIN インデックスを使う
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_users_username_trgm ON users USING gin (username gin_trgm_ops);
CREATE INDEX ix_users_name_trgm ON users USING gin (name gin_trgm_ops);
