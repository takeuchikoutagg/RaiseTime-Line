-- ユーザー（docs/ER図.md の users テーブル）
CREATE TABLE users (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- @ユーザーID。半角英小文字・数字・_ の3〜15文字。登録後は変更できない
    username      varchar(15)  NOT NULL,
    name          varchar(50)  NOT NULL,
    -- ログインID。アプリで小文字にそろえてから保存する
    email         varchar(255) NOT NULL,
    -- BCrypt でハッシュ化したパスワード
    password_hash varchar(255) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-z0-9_]{3,15}$')
);
