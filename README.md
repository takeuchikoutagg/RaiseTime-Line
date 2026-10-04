# RaiseTime Line

受講生や個人が、学習の記録や日々の気づきを気軽に投稿できる、タイムライン型のテキストSNS。X（旧Twitter）を参考に、学習目的で開発する。

X に近い使い心地だが、**インプレッション数の表示やリツイート機能はあえて作らない**。「どれだけ見られたか」「どれだけ広まったか」を気にせず、フォローしている人の投稿だけを落ち着いて読めることを目指す。いいね数とコメント数は表示するので、受け取った反応は分かる。

## 概要

- 対象ユーザー: 受講生・個人。少人数での利用を想定するが、**複数ユーザーが使う前提**で設計する（誰でも新規登録でき、他のユーザーの投稿にいいね・コメントできる）
- 技術スタック: React（Vite）+ Spring Boot（Java 25）+ PostgreSQL、DB アクセスは MyBatis、画像の保存先は AWS S3
- データ保存先: 投稿・ユーザーなどのデータは PostgreSQL、投稿画像は AWS S3（DB には S3 のキーだけを保存）
- インフラ: AWS（CloudFront / ALB / EC2 / RDS / S3）で構築する案を用意済み。AWS 上に構築するかは未定
- 学習の目的: 認証・認可、CRUD、1対多・多対多（自己参照を含む）のリレーション、画像アップロード、AWS でのインフラ構築を一通り身につける
- 実装状況: **要件定義・設計と、HTML / CSS / JavaScript のプロトタイプ（`prototype/`）まで完了**。本番の実装を「バックエンド → フロントエンド → 品質チェック → インフラ構築」の順で進めている。現在は、新規登録・ログイン・ログアウト、投稿（画像つき）・タイムライン表示・投稿の編集・削除・いいね・コメント・フォロー・ユーザーページ・ユーザー検索・プロフィール編集まで、バックエンドと画面ができている（進み具合は [要件定義.md 15章](./docs/要件定義.md#15-実装状況まとめ) を参照）

## 主な機能

| ID | 機能 | 概要 |
|---|---|---|
| F-01 | 新規登録 | 名前・@ユーザーID・メールアドレス・パスワードでアカウントを作る。登録後はそのままログイン |
| F-02 | ログイン・ログアウト | メールアドレスとパスワードで認証する |
| F-03 | タイムライン | 自分とフォロー中ユーザーの投稿を新しい順に表示する。各投稿にいいね数・コメント数を出す |
| F-04 | 投稿作成 | テキスト（1〜280文字）を投稿する。画像を最大4枚添付できる |
| F-05 | コメント | 投稿詳細画面で、コメントの一覧表示と書き込みをする |
| F-06 | いいね | いいね／いいね解除をする（1人1投稿につき1回） |
| F-07 | フォロー | 他のユーザーをフォロー／フォロー解除する（相手の承認は不要） |
| F-08 | ユーザーページ | 特定ユーザーの投稿一覧、フォロー数・フォロワー数を表示する |
| F-09 | ユーザー検索 | @ユーザーID または名前の部分一致で、フォローしたいユーザーを探す |
| F-10 | 投稿編集 | 自分の投稿の本文・画像を変更する。「編集済み」と表示する |
| F-11 | 投稿削除 | 自分の投稿を、確認のうえ削除する（画像・コメント・いいねも一緒に消える） |

### あえて作らない機能

| 機能 | 理由 |
|---|---|
| インプレッション数（表示回数）の表示 | 見られた数を気にせず投稿できるようにするため |
| リツイート（リポスト）・引用 | 拡散の仕組みを持たず、フォローしている人の投稿だけで完結させるため |
| トレンド・おすすめ表示 | アルゴリズムではなく、時系列とフォロー関係だけでタイムラインを作るため |

詳細な仕様は [docs/機能一覧.md](./docs/機能一覧.md) と [docs/機能定義書/](./docs/機能定義書/) を参照。

## 技術スタック

| 分類 | 技術 | バージョン |
|---|---|---|
| フロントエンド | React + Vite（JavaScript）、React Router、CSS Modules | React 19.2 / Vite 8.3 / React Router 8.4 |
| フロントのテスト・静的解析 | Vitest ＋ Testing Library、oxlint | Vitest 5.0 |
| バックエンド | Spring Boot（Java） | Spring Boot 4.1.0 / Java 25（Gradle 9.5.1） |
| DB アクセス | MyBatis（SQL は Mapper XML） | mybatis-spring-boot-starter 4.1.0 |
| マイグレーション | Flyway | Spring Boot 管理のバージョン |
| データベース | PostgreSQL（Docker） | PostgreSQL 17 |
| 認証 | Spring Security（OAuth2 Resource Server）で JWT。アクセストークン15分・リフレッシュトークン7日を HttpOnly Cookie で渡す。CSRF は Cookie ＋ ヘッダー方式 | Spring Security 7.1.0 |
| 画像の保存先 | AWS S3（AWS SDK for Java v2）。開発中は S3 互換の Adobe S3Mock（Docker） | AWS SDK 2.55.9 |
| インフラ（案） | AWS（CloudFront / ALB / EC2 / RDS / S3） | - |
| ドキュメント | Markdown（図は Mermaid） | - |

構成は隣の Taskmanagement（AI-java-study）にそろえている。

## システム構成（案）

```
ブラウザ ──HTTPS──> CloudFront ─┬─ /*        → S3（React のビルド成果物）
                               ├─ /images/* → S3（投稿画像）
                               └─ /api/*    → ALB → EC2（Spring Boot）→ RDS（PostgreSQL）
                                                      └─ 画像のアップロード → S3
```

画面・API・画像を CloudFront で同じドメインから配信するため、CORS の設定が不要。詳細は [docs/インフラ構成図.md](./docs/インフラ構成図.md) を参照。

## ディレクトリ構成

```
backend/    … Spring Boot（Gradle）製バックエンド
frontend/   … React + Vite 製フロントエンド
docs/       … 要件定義書と設計ドキュメント（要件定義・機能一覧・機能定義書・画面設計・ER図・インフラ構成図など）
prototype/  … HTML / CSS / JavaScript だけで作ったプロトタイプ（データは localStorage。本番の React + Spring Boot 版を作る前の画面・動きの確認用）
.claude/    … Claude Code 向けの設定・プロジェクトスキル
.githooks/  … Git フック（main への直接プッシュを防ぐ pre-push）
CLAUDE.md   … Claude Code 向けの作業ガイドライン・開発ルール
docker-compose.yml … 開発用の PostgreSQL
```

## セットアップ・起動方法

前提: Docker、Node.js、Java（`backend/` の Gradle Toolchain 設定により、ローカルに Java 25 が無くても自動で取得される）

1. PostgreSQL と S3Mock（開発用の S3）を起動: `docker compose up -d`（ポート 5434 / 9090）
2. バックエンドを起動: `backend/start.sh`（ポート 8081。使用中なら既存プロセスを停止してから `./gradlew bootRun` を起動する。開発用の設定（`dev` プロファイル＝`application-dev.properties`。JWT の秘密鍵など）を使う。本番は `JWT_SECRET` を設定しないと起動しない）
3. フロントエンドを起動: 初回だけ `cd frontend && npm install`。その後 `frontend/start.sh`（ポート 5174。使用中なら既存プロセスを停止してから `npm run dev` を起動する）
4. ブラウザで `http://localhost:5174` を開く → ログイン画面。新規登録するとそのままログインし、仮のホーム画面（Hello World）が出る

- フロントの `/api` へのリクエストは、Vite の開発サーバーがバックエンド（8081）へ転送する（`frontend/vite.config.js`）。画面と API が同じオリジンになるので、CORS の設定は不要で、ログインの Cookie もそのまま使える
- バックエンドだけの動作確認: `curl http://localhost:8081/actuator/health` → `{"status":"UP"}`、`curl http://localhost:8081/api/auth/me` → 401（ログインしていないため）
- フロントのテスト: `cd frontend && npm test`。静的解析: `npm run lint`。本番用のビルド: `npm run build`

- テーブルは起動時に Flyway が作る（`backend/src/main/resources/db/migration/`）
- テスト: `cd backend && ./gradlew test`（PostgreSQL が起動している必要がある）。静的解析: `./gradlew checkstyleMain`
- 隣の Taskmanagement（8080 / 5173 / 5433）と同時に動かせるよう、ポートを 8081 / 5174 / 5434 にしている（S3Mock は 9090）。詳細は [.claude/skills/start-dev-servers/SKILL.md](./.claude/skills/start-dev-servers/SKILL.md) を参照

### プロトタイプ（prototype/）

HTML / CSS / JavaScript だけで作ったプロトタイプで、全11機能（F-01〜F-11）をひととおり試せる。インストールやサーバーは不要。

1. `prototype/index.html` をブラウザで開く（macOS なら `open prototype/index.html`）
2. デモ用アカウントでログインする（パスワードはすべて `password123`）

| メールアドレス | @ユーザーID | 名前 |
|---|---|---|
| `taro@example.com` | `taro_dev` | 山田太郎 |
| `hanako@example.com` | `hanako` | 佐藤花子 |
| `ichiro@example.com` | `ichiro` | 鈴木一郎 |
| `s_taro@example.com` | `s_taro` | Taro S. |

プロトタイプの注意点

- データはそのブラウザの localStorage にだけ保存される（ほかのブラウザ・PC とは共有されない）。画面下の「データを初期化」で最初の状態に戻せる
- 画像は保存前に長辺1024pxの JPEG に縮小している（localStorage の容量が5MB前後のため）。容量が足りなくなったら「保存容量が足りません」と表示される
- パスワードは平文で保存している。本番では BCrypt でハッシュ化する
- 1台のブラウザで複数ユーザーを試すときは、ログアウトして別のアカウントでログインし直す

確認の手順の例

1. `taro@example.com` でログインし、タイムラインに自分と花子さんの投稿だけが出ることを確かめる
2. 画像付きで投稿 →「…」→ 編集（本文と画像）→「編集済み」が出る →「…」→ 削除
3. いいね／解除をする。投稿詳細を開いてコメントする
4. 検索で `@ichiro` を探してフォロー → タイムラインに一郎さんの投稿が出る
5. ログアウト → 新規登録（使用済みの @ユーザーID やパスワードの不一致でエラーが出る）→ 空のタイムラインが出る
6. 本文に `<script>alert(1)</script>` と投稿し、文字のまま表示される（XSS 対策の確認）
7. 画面の幅を狭めると、ナビゲーションが画面下部に移る（スマートフォン表示）

## ドキュメント

まず [docs/要件定義.md](./docs/要件定義.md) を読み、そこから各ドキュメントへ進む。

| ドキュメント | 内容 |
|---|---|
| [docs/要件定義.md](./docs/要件定義.md) | 概要・対象ユーザー・利用環境・コンセプト・技術スタック・非機能要件・制約条件・未決事項・実装状況 |
| [docs/機能一覧.md](./docs/機能一覧.md) | 全11機能の一覧と API 一覧 |
| [docs/機能定義書/](./docs/機能定義書/) | 機能ごとの詳しい仕様（入力項目・処理の流れ・業務ルール・API・エラー）。まず [00_共通仕様.md](./docs/機能定義書/00_共通仕様.md) を読む |
| [docs/ユースケース.md](./docs/ユースケース.md) | ユースケース図とユースケース一覧（UC1〜UC13） |
| [docs/画面設計.md](./docs/画面設計.md) | 画面一覧（S-01〜S-08）・画面遷移図・各画面のワイヤーフレーム |
| [docs/ER図.md](./docs/ER図.md) | ER図・テーブル定義・インデックス・主要なクエリ |
| [docs/インフラ構成図.md](./docs/インフラ構成図.md) | AWS で構築する場合の構成案・ネットワーク・権限・費用を抑える工夫 |

図はすべて [Mermaid](https://mermaid.js.org/) で書いている。GitHub ではそのまま図として表示される。VSCode では拡張機能「Markdown Preview Mermaid Support」を入れるとプレビューで表示できる。

## 開発ルール

ブランチ運用（issue ごとの作業ブランチ・Pull Request 経由でのマージ）などの開発ルールは [CLAUDE.md](./CLAUDE.md) を参照。

- `main` ブランチへの直接プッシュは禁止。GitHub Free のプライベートリポジトリではブランチ保護が使えないため、ローカルの Git フック（[.githooks/pre-push](./.githooks/pre-push)）で止めている
- Pull Request をマージしたら、作業ブランチは自動で削除される（GitHub のリポジトリ設定で有効化済み）

clone したら、最初に次のコマンドでフックを有効にする。

```bash
git config core.hooksPath .githooks
```
