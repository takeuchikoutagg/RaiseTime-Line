-- いいね（docs/ER図.md の likes テーブル、F-06）
CREATE TABLE likes (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint      NOT NULL,
    -- いいねした人
    user_id    bigint      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    -- 同じ人が同じ投稿に2回いいねできないようにする。「自分がいいね済みか」を調べるときにも使われる
    CONSTRAINT uq_likes_user_post UNIQUE (user_id, post_id),
    -- 投稿・ユーザーを消すと、いいねも一緒に消える（F-11）
    CONSTRAINT fk_likes_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 投稿ごとのいいね数を数えるためのインデックス
CREATE INDEX ix_likes_post_id ON likes (post_id);
