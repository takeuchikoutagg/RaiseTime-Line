-- コメント（docs/ER図.md の comments テーブル、F-05）
CREATE TABLE comments (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- コメント先の投稿
    post_id    bigint      NOT NULL,
    -- コメントした人
    user_id    bigint      NOT NULL,
    body       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- 絵文字も1文字と数える（char_length はコードポイント単位で数える）
    CONSTRAINT ck_comments_body_length CHECK (char_length(body) BETWEEN 1 AND 280),
    -- 投稿・ユーザーを消すと、コメントも一緒に消える（F-11）
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 投稿詳細で「その投稿のコメントを古い順に」取り、件数を数えるためのインデックス
CREATE INDEX ix_comments_post_id_id ON comments (post_id, id);
