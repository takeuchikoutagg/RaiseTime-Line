-- 投稿の画像（docs/ER図.md の post_images テーブル）。画像ファイルそのものは S3 に置き、ここにはキーだけを保存する
CREATE TABLE post_images (
    id         bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint       NOT NULL,
    -- S3 のオブジェクトキー（例：posts/{post_id}/{uuid}.jpg）
    s3_key     varchar(255) NOT NULL,
    -- 表示順（1〜4）
    position   smallint     NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_images_s3_key UNIQUE (s3_key),
    CONSTRAINT uq_post_images_post_position UNIQUE (post_id, position),
    CONSTRAINT ck_post_images_position CHECK (position BETWEEN 1 AND 4),
    -- 投稿を消すと画像の行も消える（S3 のファイルはアプリが消す）
    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);
