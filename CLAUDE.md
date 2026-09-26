# CLAUDE.md

このファイルはこのリポジトリで作業する際のガイドラインです。

## プロジェクト概要

X（旧Twitter）を参考にした、学習用のタイムライン型テキストSNS。技術スタックは React + Spring Boot（Java）+ PostgreSQL、画像の保存先は AWS S3。詳細は [README.md](./README.md) および [要件定義.md](./要件定義.md) を参照。

現在は要件定義・設計まで完了しており、実装はこれから。仕様は `docs/` 配下のドキュメント（特に [docs/機能定義書/](./docs/機能定義書/)）に従う。

## 起動コマンド

実装前のため未整備。`.claude/skills/start-dev-servers/SKILL.md` は Taskmanagement（AI-java-study）から転用したもので、`backend/start.sh`・`frontend/start.sh` を作ったらこのプロジェクト用に内容を合わせる。

## 開発ルール

### ブランチ運用

- **mainブランチに直接変更を加えない。** 作業は必ずissueごとに作業用ブランチを作成して行う。
- ブランチはissueに対応する形で作成する（例: `issue-12`、`feature/12-post-edit` など、issue番号がわかる命名にする）。
- 作業が完了したら、mainブランチへはPull Requestを経由してマージする。
- mainブランチはGitHubのブランチ保護で直接プッシュを禁止している。Pull Requestをマージすると作業ブランチは自動で削除される。

### ドキュメント

- 仕様を変えたら、関連するドキュメント（要件定義・機能一覧・機能定義書・画面設計・ER図）も同じPull Requestで更新する。
- ドキュメントは Markdown、図は Mermaid で書く。
- 「インプレッション数を表示しない」「リツイート等の拡散機能を作らない」というコンセプトに反する機能は追加しない。
