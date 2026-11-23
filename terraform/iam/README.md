# Terraform を実行する権限（IAM ポリシー）

Terraform でインフラ（[インフラ構成図](../../docs/インフラ構成図.md)）を作る IAM ユーザー `kouta` に付ける、**必要最小限**のポリシー（issue #79）。`AdministratorAccess` は付けない。

`kouta` は IAM のポリシーを作れない・自分の権限を変えられないので、ここにある JSON を、**ルートユーザー（または IAM の管理者）がコンソールで作って付ける**。何を許すかを Pull Request で確かめられるように、リポジトリに置いている。

## 1. ポリシーの一覧

| ファイル（＝ポリシーの名前） | 付ける先 | 許すこと |
|---|---|---|
| `raisetime-boundary.json` | **`kouta` には付けない**。Terraform が作る IAM ロールの上限（permissions boundary） | ECS のタスク（イメージの取得・ログ・秘密情報・画像の保存）と、GitHub Actions のデプロイ（イメージの push・タスク定義の更新・画面の配置・CloudFront のキャッシュの削除）に要るものだけ |
| `raisetime-terraform-iam.json` | `kouta` | `raisetime-*` の IAM ロールの作成・変更・削除（**上限 `raisetime-boundary` を付けるときだけ**）、それを ECS のタスクに渡すこと、GitHub Actions 用の OIDC プロバイダー、ECS・ELB・RDS のサービスリンクロール、自分の権限を見ること、ポリシーの検証 |
| `raisetime-terraform-network.json` | `kouta` | VPC・サブネット・インターネットゲートウェイ・ルートテーブル・セキュリティグループ・VPC エンドポイント（東京だけ。作るときはタグ `Project=raisetime` が必須、変更・削除はそのタグがあるものだけ） |
| `raisetime-terraform-compute.json` | `kouta` | ECR のリポジトリ（`raisetime-*`）、ECS のクラスター（`raisetime`）・サービス・タスク定義（`raisetime-*`）、ALB・ターゲットグループ・リスナー（`raisetime-*`） |
| `raisetime-terraform-data.json` | `kouta` | S3 のバケット（`raisetime-*`。state・画面・画像）、RDS（作るときはタグ必須、変更・削除は `raisetime-*` だけ）、RDS が管理するパスワード（Secrets Manager の `rds!*`）、Parameter Store（`/raisetime/*`） |
| `raisetime-terraform-edge.json` | `kouta` | CloudFront の配信・OAC・キャッシュ／オリジンリクエスト／レスポンスヘッダーのポリシー・キャッシュの削除 |
| `raisetime-terraform-monitoring.json` | `kouta` | CloudWatch Logs（`/ecs/raisetime-*`）、アラーム（`raisetime-*`）、SNS（`raisetime-*`）、Route 53 のヘルスチェック、コストの異常検知を見ること |

今ある `AWSBillingReadOnlyAccess` と合わせて、`kouta` に付くポリシーは7つ（1人のユーザーに付けられるのは標準で10まで）。

## 2. Terraform のコードで守る決まり

ポリシーは、名前とタグで絞っている。コードがこの決まりを外れると、**権限が足りずに失敗する**（失敗するのは正しい動き）。

| 決まり | 理由 |
|---|---|
| 作るものの名前は `raisetime-` で始める（ECS のクラスターは `raisetime`、ロググループは `/ecs/raisetime-*`、Parameter Store は `/raisetime/*`） | ポリシーの `Resource` を名前で絞っている |
| すべてにタグ `Project=raisetime` を付ける（プロバイダーの `default_tags` で付ける） | VPC などと RDS は、作るときにこのタグが無いと断る。変更・削除はこのタグがあるものだけ |
| リージョンは東京（ap-northeast-1）。Route 53 のヘルスチェックのアラームと SNS だけ us-east-1 | ポリシーをリージョンで絞っている |
| IAM ロールには、必ず `permissions_boundary = raisetime-boundary` を付ける | 付けないと、ロールを作れない（下の 4章） |
| 秘密の値（`JWT_SECRET` など）は Terraform で作らない。`aws ssm put-parameter` で入れ、Terraform は名前だけを使う。RDS のパスワードは RDS に管理させる（`manage_master_user_password`） | state に秘密の値を残さないため |

## 3. 作り方（ルートユーザーで、コンソールから）

**順番が大事**：`raisetime-boundary` を先に作る（ほかのポリシーが、この名前を使うため）。

1. 「**IAM**」→「**ポリシー**」→「**ポリシーの作成**」→「**JSON**」
2. ファイルの中身を全部貼り付ける。エディタが警告やエラーを出したら、その内容を教えてもらう（直してから作る）
3. 「**次へ**」→ ポリシー名に**ファイル名から `.json` を取ったもの**（例：`raisetime-boundary`）を入れて「**ポリシーの作成**」
4. 7つとも作る
5. 「**ユーザー**」→「**kouta**」→「**許可を追加**」→「**ポリシーを直接アタッチする**」で、`raisetime-terraform-` で始まる**6つ**を付ける（**`raisetime-boundary` は付けない**）

## 4. 権限の昇格を防ぐ仕組み

最小限の権限でも、IAM のロールを作れると、`kouta` が「自分より強いロール」を作って使うことができてしまう。それを、次の3つで防いでいる。

1. `kouta` が作れるロールは `raisetime-*` だけで、**上限 `raisetime-boundary` を付けたときだけ**作れる・変えられる（`iam:PermissionsBoundary` の条件）。ロールの権限は、ロールのポリシーと上限の**重なった部分だけ**になる
2. 上限を外す操作（`iam:DeleteRolePermissionsBoundary`）と、ポリシーの中身を書き換える操作（`iam:CreatePolicyVersion` など）は、**明示的に拒否**している。`kouta` は上限そのものを広げられない
3. `kouta` 自身の権限を変える操作（`iam:AttachUserPolicy` など）は、そもそも許していない

**残っている弱いところ**（学習用として受け入れる）：
- `kouta` は、上限の範囲内のロールを作って自分で使える。上限には RDS のパスワードを読む権限（`secretsmanager:GetSecretValue`）が入っているので、その方法で DB のパスワードを読める。`kouta` はこの DB を管理する人なので、問題にしない
- S3 は `raisetime-*` のバケットに対してすべての操作を許している（バケットの設定の操作が多いため）。バケットを公開する設定もできてしまうので、Terraform で公開をブロックする設定（Block Public Access）を必ず付ける
- セキュリティグループのルールの追加・削除は、リージョンでだけ絞っている（新しく作るルールにはタグが付かず、タグで絞れないため）

## 5. 権限が足りなかったとき

最初から完璧にはできないので、`terraform plan`・`apply` で断られたら、次のように足す。

1. エラーに出る操作の名前（例：`ec2:CreateNetworkAcl`）と、対象（ARN）を確かめる
2. 本当に要る操作かを考える（コードの書き方を変えれば要らない場合もある）
3. この JSON に足して、Pull Request で確かめる
4. ルートユーザーが、コンソールでそのポリシーを開いて「**編集**」し、新しい JSON を貼り付ける（ポリシーの版は5つまで。古い版を消すように言われたら消す）
