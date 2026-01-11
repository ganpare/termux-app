# Termux application (Fork) - 日本語

[English](./README.md) | **日本語**

> **🔀 このリポジトリは [termux/termux-app](https://github.com/termux/termux-app) のフォーク版です。**
>
> このフォークでは、SSH接続管理や Byobu セッション制御など、リモートサーバーで頻繁に作業するパワーユーザー向けのカスタムUI機能が追加されています。

## ✨ 追加機能

### SSH 接続管理
- **SSH ボタン**: 保存されたSSH接続へのクイックアクセス
- **SSH 設定**: SSH接続設定の追加、編集、管理
- **SSH キーサポート**: SSH秘密鍵のインポートと管理

### Byobu セッション連携
- **SESSIONS ボタン**: Byobuセッションの一覧表示、アタッチ、新規作成、終了
- **HELP ボタン**: Byobuコマンドのクイックリファレンス（コピー＆実行機能付き）
- 二重セッションの自動防止機能

### カスタムコマンド
- **CUSTOM ボタン**: よく使うコマンドを保存・実行
- カスタムコマンドの追加、編集、削除、並べ替え
- ワンタップでのコマンド実行
- **開発者プリセット**: `claude`, `cursor`, `codex` などの主要なAIエージェント用コマンドが、起動時に「AI Agents」フォルダに自動登録されます。
- **自動化**: 開発者は `CustomCommandManager.java#ensureDefaultCommands()` で永続的なプリセットを追加定義できます。

## 📋 UI ボタン一覧

| ボタン | 色 | 機能 |
|--------|-------|------|
| SESSIONS | 青 | Byobu セッション管理 |
| CUSTOM | オレンジ | カスタムコマンドの実行 |
| HELP | 紫 | Byobu コマンドリファレンス |
| SSH | 緑 | 保存済みSSHホストへの接続 |
| SSH設定 | グレー | SSH設定の管理 |

---

*以下は [termux/termux-app](https://github.com/termux/termux-app) のオリジナルREADME（英語）です：*

---

[元のREADMEを表示](./README.md#termux-application-original)
