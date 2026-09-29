-- リフレッシュトークン（docs/ER図.md の refresh_tokens テーブル）
-- トークンそのものは保存せず、SHA-256 のハッシュだけを保存する（DB が漏れても使えないようにするため）
CREATE TABLE refresh_tokens (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL,
    -- トークンの SHA-256（16進数64文字）
    token_hash  char(64)    NOT NULL,
    -- ログイン1回ごとの系列。交換しても引き継ぐ。使用済みのトークンが再び使われたら、この系列をまとめて無効にする
    family_id   uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    -- 交換・ログアウト・無効化された日時。NULL なら使える
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
