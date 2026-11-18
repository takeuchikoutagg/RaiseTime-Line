# CLAUDE.md

このファイルはこのリポジトリで作業する際のガイドラインです。

## プロジェクト概要

X（旧Twitter）を参考にした、学習用のタイムライン型テキストSNS。技術スタックは React + Spring Boot（Java）+ PostgreSQL、画像の保存先は AWS S3。詳細は [README.md](./README.md) および [docs/要件定義.md](./docs/要件定義.md) を参照。

要件定義・設計・プロトタイプ（`prototype/`）まで完了し、本番実装を「バックエンド → フロントエンド → 品質チェック → インフラ構築」の順で進めている。仕様は `docs/` 配下のドキュメント（特に [docs/機能定義書/](./docs/機能定義書/)）に従う。

## 技術構成

- バックエンド：Spring Boot 4.1.0 / Java 25 / Gradle。DB アクセスは **MyBatis**（SQL は `src/main/resources/mapper/*.xml`。JPA は使わない）
- フロントエンド：React + Vite（JavaScript）、React Router、CSS Modules。テストは Vitest ＋ Testing Library、E2E テストは Playwright（`frontend/e2e/`、`npm run e2e`。Chromium・WebKit・Microsoft Edge。アクセシビリティは axe-core）、静的解析は oxlint。ブラウザのパフォーマンステストは `frontend/e2e-perf/`（`npm run e2e:perf`。毎回は動かさない）
- 画像：AWS S3（AWS SDK v2）。開発中は S3 互換の Adobe S3Mock（Docker Compose、認証なし）。画像ファイルは S3、DB には S3 のキーだけを保存する
- DB：PostgreSQL 17（Docker Compose）。テーブルは **Flyway**（`src/main/resources/db/migration/`）だけで作る
- ログ：本番は JSON の構造化ログ（`logging.structured.format.console=logstash`）、開発（dev プロファイル）は文章。ログを足すときは `LogEvents` の `event` と項目を `addKeyValue` で付ける。**パスワード・トークン・Cookie・メールアドレスそのもの・本文はログに出さない**（メールアドレスは `LogMasks.email`）。設計は [docs/ログ・監視設計.md](./docs/ログ・監視設計.md)
- 認証：JWT（Spring Security の OAuth2 Resource Server）。アクセストークン（JWT、15分）とリフレッシュトークン（7日、DB にハッシュを保存して使うたびに交換）を、どちらも HttpOnly Cookie で渡す。CSRF 対策は Cookie ＋ ヘッダー方式
  - **`oauth2ResourceServer()` は使わない。** 使うと「トークン付きのリクエストは CSRF のチェックを省く」設定が自動で入り、Cookie でトークンを渡すこのアプリでは CSRF 攻撃ができてしまう（issue #15）。JWT の検証フィルターは `SecurityConfig` で自分で組み立てている

## 起動コマンド

起動方法の詳細は [README.md](./README.md#セットアップ起動方法) を参照。

- PostgreSQL・S3Mock（開発用の S3）: `docker compose up -d`（ポート 5434 / 9090）
- バックエンド: `cd backend && ./start.sh`（ポート 8081）
- フロントエンド: `cd frontend && ./start.sh`（ポート 5174。`/api` はバックエンドへプロキシ）

**サーバー起動時のポートに関するルール（必須）:**

- サーバーは必ず `start.sh` 経由で起動する。`./gradlew bootRun` や `npm run dev` を直接実行しない。
- ポートが競合していても、別のポートに逃がして起動してはならない。対象ポート（バックエンド 8081・フロントエンド 5174）を使用しているプロセスを停止してから、同じポートで起動する。フロントエンド（Vite）は `vite.config.js` で `server.strictPort: true` を設定しており、ポートが空いていないと別ポートに逃げずにエラーになる。
- 隣の Taskmanagement プロジェクトが 8080 / 5173 / 5433 を使っているため、このプロジェクトは 8081 / 5174 / 5434 を使う。**Taskmanagement のポートのプロセスは止めない。**
- 本番用のビルドを配るプレビュー（ブラウザのパフォーマンステスト用）は `frontend/preview.sh` でポート 4174 に起動する。`npm run preview` を直接実行しない（start.sh と同じく、ポートが使用中なら止めてから同じポートで起動する）
- 具体的な起動手順は `.claude/skills/start-dev-servers/SKILL.md` にスキルとして定義している。サーバー起動・動作確認が必要な場合はこのスキルに従う。
- バックエンドのテスト（`./gradlew test`）は PostgreSQL が起動している必要がある。
- 性能テスト（k6）は毎回は動かさず、必要なときに `perf/run.sh` で実行する。バックエンドは `perf/run.sh backend` で、性能テスト用の DB `raisetime_perf` に向けて起動する（中で `backend/start.sh` を呼ぶ。ポートは 8081 のまま）。**開発用の DB `raisetime` に大量データを入れない。** 終わったら `backend/start.sh` で起動し直す。手順は [docs/パフォーマンステスト.md](./docs/パフォーマンステスト.md)

## 開発ルール

### ブランチ運用

- **mainブランチに直接変更を加えない。** 作業は必ずissueごとに作業用ブランチを作成して行う。
- ブランチはissueに対応する形で作成する（例: `issue-12`、`feature/12-post-edit` など、issue番号がわかる命名にする）。
- 作業が完了したら、mainブランチへはPull Requestを経由してマージする。
- main は GitHub のブランチ保護で守っている：Pull Request からしか変更できない（直接 push・強制 push・削除は不可。管理者にも適用）。CI の5つのジョブ（backend・frontend・e2e の chromium／webkit／msedge）がすべて成功し、main の最新を取り込んでいないとマージできない（承認者の人数は0人）。
- ローカルのGitフック（`.githooks/pre-push`、`git config core.hooksPath .githooks` で有効化）でも、main への直接プッシュを push する前に止める。フックを `--no-verify` で回避しない。
- Pull Requestをマージすると作業ブランチは自動で削除される。
- Pull Request（main 向け）と main へのマージのたびに、CI（`.github/workflows/ci.yml`）が静的解析（Checkstyle・oxlint の警告も含む）・単体／結合テスト・E2E（Chromium・WebKit・Edge）を実行する。**CI がすべて通ってからマージする。** main で CI が成功すると、デプロイ（`.github/workflows/deploy.yml`）が AWS へ自動でデプロイする（リポジトリ変数 `DEPLOY_ENABLED` が `true` のときだけ。手順は [docs/デプロイ.md](./docs/デプロイ.md)）。 CI があっても、Pull Request を出す前に手元で確かめる決まり（テスト・ログの運用ルール 3章）は変わらない。パフォーマンステストは CI では動かさない

### テスト・ログの運用

- 機能を足す・直すときは [docs/テスト・ログの運用ルール.md](./docs/テスト・ログの運用ルール.md) に従う。要点：
  - **実装の前に、テストケースを技法で書き出す**（入力 → 境界値、条件の組み合わせ → デシジョンテーブル、状態 → 状態遷移）。[docs/テスト設計書.md](./docs/テスト設計書.md) の表に足す
  - **バグを直すときは、先に再現するテストを書く**。テスト設計書 5章に記録し、似た場所を探す
  - ログを足すときは、いつ・どのレベルで出るか、出してはいけないものが出ないかをテストする（`LogCapture`）
  - 網羅率を下げない。業務のルールを持つクラス（`service`・`validation`・`LoginAttemptService`・画像処理）は分岐網羅 100%（`./gradlew test coverageSummary`）
- Pull Request の本文は `.github/pull_request_template.md` のチェックリストに沿って書く

### ドキュメント

- 仕様を変えたら、関連するドキュメント（要件定義・機能一覧・機能定義書・画面設計・ER図・テスト観点表）も同じPull Requestで更新する。
- ドキュメントは Markdown、図は Mermaid で書く。
- API（パス・パラメーター・リクエスト・レスポンス）を変えたら、`cd backend && ./gradlew generateOpenApi` で API 仕様書（`docs/api/openapi.yaml`）を作り直して同じPull Requestに含める。作り直し忘れは `OpenApiDocumentTest` が失敗して知らせる。
- 「インプレッション数を表示しない」「リツイート等の拡散機能を作らない」というコンセプトに反する機能は追加しない。
