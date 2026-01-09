# HTTP File Transfer Architecture

## Overview

このドキュメントは、SSH接続先サーバーからHTTP経由でファイルを取得し、ARグラスに表示するパイプラインの設計と使用方法を説明します。

## Architecture

```
┌─────────────────┐     SSH      ┌─────────────────┐
│   Android App   │─────────────▶│  Remote Server  │
│    (Termux)     │              │                 │
└────────┬────────┘              └────────┬────────┘
         │                                │
         │  HTTP (port 8765)              │ Python HTTP Server
         │◀──────────────────────────────▶│ (claude-history-server.py)
         │                                │
         ▼                                ▼
┌─────────────────┐              ┌─────────────────┐
│  Local Storage  │              │ ~/.claude/      │
│  ~/claude-      │              │   projects/     │
│    history/     │              │                 │
└────────┬────────┘              └─────────────────┘
         │
         │ Parse & Send
         ▼
┌─────────────────┐
│  EVEN G1 AR     │
│  Glasses (BLE)  │
└─────────────────┘
```

## Components

### 1. Server Side: `claude-history-server.py`

**Location:** `~/.local/bin/claude-history-server.py`

**Endpoints:**
| Endpoint | Description |
|----------|-------------|
| `GET /health` | ヘルスチェック、CWD返却 |
| `GET /api/projects` | 全プロジェクト一覧 |
| `GET /api/files/current` | 現在のCWDに対応するファイル一覧 |
| `GET /api/latest` | 最新ファイルをダウンロード |
| `GET /api/download?file=<path>` | 指定ファイルをダウンロード |

**起動方法:**
```bash
# 直接起動
python3 ~/.local/bin/claude-history-server.py 8765

# byobuセッションで起動（推奨）
byobu new-session -d -s claude-history-server 'python3 ~/.local/bin/claude-history-server.py 8765'
```

### 2. Client Side: `ClaudeHistoryHttpClient.java`

**Location:** `app/src/main/java/com/termux/app/claude/ClaudeHistoryHttpClient.java`

**主要メソッド:**
```java
// ヘルスチェック
void checkHealth(HealthCallback callback)

// ファイル一覧取得
void listCurrentFiles(FileListCallback callback)

// 最新ファイルダウンロード
void downloadLatest(DownloadCallback callback)

// 指定ファイルダウンロード
void downloadFile(RemoteFile file, DownloadCallback callback)
```

## Usage Flow

### 1. SSH接続
```
SSHボタン → 接続先選択 → 接続
（ホストアドレスが自動保存される）
```

### 2. HTTPサーバー起動（サーバー側）
```bash
# 対象プロジェクトディレクトリで実行
cd ~/your-project
python3 ~/.local/bin/claude-history-server.py
```

### 3. ファイル取得
```
Claudeボタン → HTTP選択 → 最新ファイルをDL or 一覧表示
```

### 4. AR表示
```
ダウンロード完了 → 送信ボタン → ARグラスに表示
```

## Extending for Other Use Cases

### 汎用HTTPファイルサーバーとして使用

`claude-history-server.py`を参考に、任意のファイルタイプに対応するサーバーを作成可能：

```python
# 例: ログファイルサーバー
class LogFileHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/logs":
            # /var/log/ からログ一覧を返す
            pass
        elif self.path.startswith("/api/download"):
            # 指定ログをダウンロード
            pass
```

### 汎用HTTPクライアントとして使用

`ClaudeHistoryHttpClient`を参考に、汎用クライアントを作成：

```java
public class GenericHttpClient {
    private final String baseUrl;
    
    public void fetchJson(String endpoint, JsonCallback callback) { ... }
    public void downloadFile(String endpoint, FileCallback callback) { ... }
}
```

## Configuration

### Android Manifest

HTTP平文通信を許可する設定が必要：
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

### Network

- デフォルトポート: `8765`
- サーバーは `0.0.0.0` でリッスン（全インターフェース）
- Tailscale等のVPN経由でもアクセス可能

## Security Considerations

1. **平文HTTP**: 現在は暗号化なし。信頼できるネットワーク内でのみ使用
2. **パスバリデーション**: サーバーは `~/.claude/projects` 配下のみアクセス許可
3. **認証なし**: 現在は認証機能なし。必要に応じて追加

## Troubleshooting

### 接続できない
1. サーバーが起動しているか確認: `curl http://localhost:8765/health`
2. ファイアウォール設定確認
3. AndroidManifestに`usesCleartextTraffic="true"`があるか確認

### ファイルが見つからない
1. サーバーのCWDを確認: `/health`エンドポイントで確認
2. `~/.claude/projects/`の構造を確認
3. プロジェクトディレクトリ名の変換ルールを確認

### ARグラスに表示されない
1. Bluetooth接続を確認
2. G1グラスがペアリングされているか確認
3. 表示テキストが空でないか確認
