-- 投稿（docs/ER図.md の posts テーブル）
CREATE TABLE posts (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL,
    body       text        NOT NULL,
    -- 投稿者が最後に編集した日時（F-10）。編集していなければ NULL
    edited_at  timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- 絵文字も1文字と数える（char_length はコードポイント単位で数える）
    CONSTRAINT ck_posts_body_length CHECK (char_length(body) BETWEEN 1 AND 280),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- タイムライン・ユーザーページで「その人の投稿を新しい順に」取るためのインデックス
CREATE INDEX ix_posts_user_id_id ON posts (user_id, id DESC);
