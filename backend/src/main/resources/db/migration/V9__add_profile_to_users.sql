-- プロフィール編集（F-12）：自己紹介文とプロフィール画像を追加する。
-- あわせて @ユーザーID（username）も登録後に変更できるようにした（形式・重複のチェックは今までどおり CHECK 制約と UNIQUE 制約で行う）
ALTER TABLE users
    -- 自己紹介文。空でもよい。絵文字も1文字と数えて160文字まで
    ADD COLUMN bio        text         NOT NULL DEFAULT '',
    -- プロフィール画像の S3 のキー（例：avatars/{user_id}/{uuid}.png）。設定していなければ NULL
    ADD COLUMN avatar_key varchar(255),
    ADD CONSTRAINT ck_users_bio_length CHECK (char_length(bio) <= 160),
    ADD CONSTRAINT uq_users_avatar_key UNIQUE (avatar_key);
