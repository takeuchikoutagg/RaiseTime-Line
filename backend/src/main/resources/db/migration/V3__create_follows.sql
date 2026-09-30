-- フォロー（docs/ER図.md の follows テーブル）
CREATE TABLE follows (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- フォローする人
    follower_id bigint      NOT NULL,
    -- フォローされる人
    followee_id bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    -- 同じ人を2回フォローできない（F-07）
    CONSTRAINT uq_follows_follower_followee UNIQUE (follower_id, followee_id),
    -- 自分自身はフォローできない（F-07）
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE
);

-- フォロワー数の集計用（フォロー数と「フォロー中か」は UNIQUE 制約のインデックスを使う）
CREATE INDEX ix_follows_followee_id ON follows (followee_id);
