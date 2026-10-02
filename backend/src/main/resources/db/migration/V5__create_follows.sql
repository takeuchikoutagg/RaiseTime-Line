-- フォロー（docs/ER図.md の follows テーブル）。
-- タイムライン（F-03）は「自分＋フォロー中の人」の投稿を出すため、先にテーブルだけ作る。フォローの API（F-07）はあとで作る
CREATE TABLE follows (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- フォローする人
    follower_id bigint      NOT NULL,
    -- フォローされる人
    followee_id bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_follows_follower_followee UNIQUE (follower_id, followee_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_follows_followee_id ON follows (followee_id);
