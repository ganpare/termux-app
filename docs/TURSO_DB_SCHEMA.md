# Turso データベーススキーマ & 連携ガイド

このドキュメントでは、Claude Code の会話履歴（`.jsonl` ファイル）を Turso (SQLite) データベースにバックアップするためのスキーマ構造と同期ロジックについて説明します。

## 1. 概要
この連携機能により、以下のことが可能になります：
- **自動バックアップ**: ローカルの会話ログをクラウドに自動的に同期します。
- **構造化データ保存**: Raw形式のJSONログを解析し、SQLクエリで扱いやすいリレーショナルテーブルに格納します。
- **増分同期**: 新しい会話ターンのみをアップロードすることで、帯域幅と処理時間を最小限に抑えます。
- **コンテキスト情報の保存**: 各ターンにおけるプロジェクト情報のメタデータ（カレントディレクトリ、Gitブランチなど）を記録します。

## 2. データベーススキーマ
データの完全性とアクセスのしやすさを両立させるため、主要な3つのテーブルで構成されています。

### 2.1 `raw_jsonl_lines` (生データ / バックアップ)
`.jsonl` ファイルの各行をそのまま 1対1 で保存するテーブルです。将来的にスキーマ定義が変わった場合でも、この「信頼できる情報源（Source of Truth）」から再解析できるようにします。

| カラム名 | 型 | 説明 |
|--------|----|------|
| `session_id` | TEXT (PK) | `.jsonl` のファイル名 (例: `5f3a...jsonl`)。セッションIDとして機能します。 |
| `line_no` | INTEGER (PK) | ファイル内の行番号 (1から開始)。 |
| `ts` | TEXT | イベントのタイムスタンプ。 |
| `uuid` | TEXT | イベント固有のUUID。 |
| `type` | TEXT | イベントタイプ (例: `user`, `assistant`, `tool_use`)。 |
| `raw_json` | TEXT | 元の完全なJSON文字列。 |

### 2.2 `turns` (ユーザープロンプト)
ユーザーによって開始された論理的な「ターン」を表します。ユーザーがその時何をしていたかというコンテキスト情報を保持します。

| カラム名 | 型 | 説明 |
|--------|----|------|
| `turn_id` | INTEGER (PK) | 自動インクリメントされる主キー。 |
| `session_id` | TEXT | `raw_jsonl_lines` への外部キー。 |
| `user_uuid` | TEXT (Unique)| ユーザーメッセージイベントのUUID。 |
| `ts` | TEXT | タイムスタンプ。 |
| `cwd` | TEXT | カレントワーキングディレクトリ (プロジェクトのパス)。 |
| `git_branch` | TEXT | ターン開始時のアクティブなGitブランチ名。 |
| `user_text` | TEXT | ユーザーが送信した実際のプロンプトテキスト。 |

### 2.3 `assistant_texts` (AIの応答)
AI（アシスタント）によるテキスト応答を保存します。AIの応答はツール使用や思考ステップによって複数のパートに分かれることがあるため、1つのユーザーターンに対して複数の行が紐付く場合があります。

| カラム名 | 型 | 説明 |
|--------|----|------|
| `assistant_text_id`| INTEGER (PK) | 自動インクリメントされる主キー。 |
| `session_id` | TEXT | セッションIDへの外部キー。 |
| `turn_id` | INTEGER (FK) | `turns` テーブルへのリンク。親ターンが不明確な場合はNULL可。 |
| `assistant_uuid` | TEXT | アシスタントメッセージイベントのUUID。 |
| `parent_uuid` | TEXT | この応答のきっかけとなったと思われる親メッセージのUUID。 |
| `ts` | TEXT | タイムスタンプ。 |
| `model` | TEXT | 使用されたモデル名 (例: `claude-3-5-sonnet`)。 |
| `stop_reason` | TEXT | 生成が停止した理由 (`end_turn`:完了, `tool_use`:ツール使用 など)。 |
| `part_index` | INTEGER | 完全な応答内でのこのテキストパートの順序。 |
| `text` | TEXT | 応答の内容（テキスト）。 |

## 3. 同期フロー

### トリガー (実行タイミング)
「Claude→AR (自動同期)」機能が完了し、ARグラスへの表示が成功した直後に `TermuxActivity` 内で自動的にキックされます。

### 内部ロジック (`TursoSyncManager.java`)
1.  **状態確認**: 現在の `session_id` に対して、データベースに保存されている最大の行番号 (`line_no`) を問い合わせます。
    ```sql
    SELECT MAX(line_no) FROM raw_jsonl_lines WHERE session_id = ?
    ```
2.  **ローカルファイルの読み込み**: ローカルの `.jsonl` ファイルを1行ずつ読み込みます。
3.  **新規行の処理**: DB内の最大行番号より大きい行（新しい行）のみを処理対象とします。
    - **Rawデータの挿入**: `raw_jsonl_lines` にそのまま挿入します。
    - **解析と紐付け**:
        - **ユーザーメッセージ**の場合: パースして `turns` テーブルに挿入します。生成された `turn_id` はメモリ上で `uuid` と紐付けてキャッシュします。
        - **アシスタントメッセージ**の場合: 親となる `turn_id` をキャッシュから検索し、`assistant_texts` テーブルに挿入します。

## 4. 活用Tips (SQLクエリ例)

**特定のセッションの完全な会話履歴（ユーザー＋AI）を取得する:**
```sql
SELECT 
  t.ts, 
  t.user_text AS ユーザー発言, 
  a.text AS AI応答 
FROM turns t
LEFT JOIN assistant_texts a ON t.turn_id = a.turn_id
WHERE t.session_id = '対象のファイル名.jsonl'
ORDER BY t.turn_id, a.part_index;
```

**特定のGitブランチに関連するプロンプトを検索する:**
```sql
SELECT session_id, user_text 
FROM turns 
WHERE git_branch = 'feature/turso-integration';
```
