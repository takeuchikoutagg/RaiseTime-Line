# CLAUDE.md

このファイルはこのリポジトリで作業する際のガイドラインです。

## プロジェクト概要

X（旧Twitter）を参考にした、学習用のタイムライン型テキストSNS。技術スタックは React + Spring Boot（Java）+ PostgreSQL、画像の保存先は AWS S3。詳細は [README.md](./README.md) および [docs/要件定義.md](./docs/要件定義.md) を参照。

要件定義・設計・プロトタイプ（`prototype/`）まで完了し、本番実装を「バックエンド → フロントエンド → 品質チェック → インフラ構築」の順で進めている。仕様は `docs/` 配下のドキュメント（特に [docs/機能定義書/](./docs/機能定義書/)）に従う。

## 技術構成

- バックエンド：Spring Boot 4.1.0 / Java 25 / Gradle。DB アクセスは **MyBatis**（SQL は `src/main/resources/mapper/*.xml`。JPA は使わない）
- フロントエンド：React + Vite（JavaScript）、React Router、CSS Modules。テストは Vitest ＋ Testing Library、静的解析は oxlint
- DB：PostgreSQL 17（Docker Compose）。テーブルは **Flyway**（`src/main/resources/db/migration/`）だけで作る
- 認証：JWT（Spring Security の OAuth2 Resource Server）。アクセストークン（JWT、15分）とリフレッシュトークン（7日、DB にハッシュを保存して使うたびに交換）を、どちらも HttpOnly Cookie で渡す。CSRF 対策は Cookie ＋ ヘッダー方式
  - **`oauth2ResourceServer()` は使わない。** 使うと「トークン付きのリクエストは CSRF のチェックを省く」設定が自動で入り、Cookie でトークンを渡すこのアプリでは CSRF 攻撃ができてしまう。JWT の検証フィルターは `SecurityConfig` で自分で組み立てている

## 起動コマンド

起動方法の詳細は [README.md](./README.md#セットアップ起動方法) を参照。

- PostgreSQL: `docker compose up -d`（ポート 5434）
- バックエンド: `cd backend && ./start.sh`（ポート 8081）
- フロントエンド: `cd frontend && ./start.sh`（ポート 5174。`/api` はバックエンドへプロキシ）

**サーバー起動時のポートに関するルール（必須）:**

- サーバーは必ず `start.sh` 経由で起動する。`./gradlew bootRun` や `npm run dev` を直接実行しない。
- ポートが競合していても、別のポートに逃がして起動してはならない。対象ポート（バックエンド 8081・フロントエンド 5174）を使用しているプロセスを停止してから、同じポートで起動する。フロントエンド（Vite）は `vite.config.js` で `server.strictPort: true` を設定しており、ポートが空いていないと別ポートに逃げずにエラーになる。
- 隣の Taskmanagement プロジェクトが 8080 / 5173 / 5433 を使っているため、このプロジェクトは 8081 / 5174 / 5434 を使う。**Taskmanagement のポートのプロセスは止めない。**
- 具体的な起動手順は `.claude/skills/start-dev-servers/SKILL.md` にスキルとして定義している。サーバー起動・動作確認が必要な場合はこのスキルに従う。
- バックエンドのテスト（`./gradlew test`）は PostgreSQL が起動している必要がある。

## 開発ルール

### ブランチ運用

- **mainブランチに直接変更を加えない。** 作業は必ずissueごとに作業用ブランチを作成して行う。
- ブランチはissueに対応する形で作成する（例: `issue-12`、`feature/12-post-edit` など、issue番号がわかる命名にする）。
- 作業が完了したら、mainブランチへはPull Requestを経由してマージする。
- mainブランチへの直接プッシュは、ローカルのGitフック（`.githooks/pre-push`、`git config core.hooksPath .githooks` で有効化）で止めている。GitHub Freeのプライベートリポジトリのためブランチ保護は使えない。フックを `--no-verify` で回避しない。
- Pull Requestをマージすると作業ブランチは自動で削除される。

### ドキュメント

- 仕様を変えたら、関連するドキュメント（要件定義・機能一覧・機能定義書・画面設計・ER図）も同じPull Requestで更新する。
- ドキュメントは Markdown、図は Mermaid で書く。
- 「インプレッション数を表示しない」「リツイート等の拡散機能を作らない」というコンセプトに反する機能は追加しない。
