# 8x9craft2 Extension

<p align="center">
  <img src="./Screenshot.png" width="350" alt="Screenshot of 8x9craft2">
</p>

[English](#english) | [日本語](#japanese)

<a name="english"></a>
# English

## Overview

This is a VSCode extension for hackCraft2, a tool for learning programming through Minecraft.

## Installation

1. Open VSCode
2. Open the Extensions view (Ctrl/Cmd + Shift + X)
3. Search for "hackCraft2"
4. Click Install
5. Restart VSCode

## Usage

### 1. Configure Server Connection

1. Open VSCode Settings (Ctrl/Cmd + ,)
2. Search for "hackCraft2"
3. Set your server host (e.g., "localhost")
   - The default port (25570) is already configured
4. Set your Minecraft player ID

### 2. Connect to Server

1. Click "hackCraft2: Disconnected" in the VSCode status bar
2. The extension will automatically connect using your configured settings
3. Once connected, you can select a pet (the first pet is selected by default)

### 3. Programming

You can program using the following languages:
- TypeScript (.ts)
- JavaScript (.js)
- Python (.py)

Example:
```javascript
// Draw a 4x4 square
for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 4; j++) {
        entity.forward(1)
    }
    entity.turnLeft()
}
```

### 4. Run Script

1. Create a new JavaScript file in VSCode
2. Write your program
3. Click the "Run" button in the top-right corner of the editor
4. Watch your pet move in Minecraft!

## Features

- Multiple programming languages support (TypeScript, JavaScript, Python)
- Entity control (movement, rotation, etc.)
- 3D view display
- Server connection management
- Script execution/stop
- Automatic pet selection
- Easy-to-use run button in the editor

## Troubleshooting

- If you can't connect:
  - Verify your server settings in VSCode Settings
  - Check if the Minecraft server is running
  - Check firewall settings

- If the script doesn't run:
  - Make sure you're connected to the server
  - Verify that a pet is selected
  - Check for syntax errors in your code

## Links

- [8x9craft2 Official Website](http://craft2.8x9.jp/en/)
- [8x9craft2 API Reference](http://wiki.craft2.8x9.jp/wiki/Category:APIs)
- [8x9.jp](http://8x9.jp/)

---

<a name="japanese"></a>
# 日本語

## 概要

hackCraft2のVSCode拡張機能です。Minecraftを通じてプログラミングを学ぶためのツールです。

## インストール方法

1. VSCodeを開く
2. 拡張機能ビュー（Ctrl/Cmd + Shift + X）を開く
3. "hackCraft2" を検索
4. インストールボタンをクリック
5. VSCodeを再起動

## 使い方

### 1. サーバー接続の設定

1. VSCodeの設定を開く（Ctrl/Cmd + ,）
2. "hackCraft2" で検索
3. サーバーのホスト名を設定（例: "localhost"）
   - ポート番号（25570）はデフォルトで設定済みです
4. MinecraftのプレイヤーIDを設定

### 2. サーバーへの接続

1. VSCodeのステータスバーにある「hackCraft2:未接続」をクリック
2. 設定した情報で自動的に接続されます
3. 接続後、ペットを選択できます（デフォルトで最初のペットが選択されます）

### 3. プログラミング

以下の言語でプログラミングができます：
- TypeScript (.ts)
- JavaScript (.js)
- Python (.py)

例：
```javascript
// 4x4の正方形を描く
for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 4; j++) {
        entity.forward(1)
    }
    entity.turnLeft()
}
```

### 4. スクリプトの実行

1. VSCodeで新しいJavaScriptファイルを作成
2. プログラムを書く
3. エディタ右上の実行ボタンをクリック
4. Minecraftでペットの動きを確認！

## 機能

- 複数のプログラミング言語（TypeScript, JavaScript, Python）に対応
- エンティティの操作（移動、回転など）
- 3Dビューの表示
- サーバー接続状態の管理
- スクリプトの実行/停止
- ペットの自動選択
- エディタ内の使いやすい実行ボタン

## トラブルシューティング

- 接続できない場合：
  - VSCodeの設定でサーバー設定を確認
  - Minecraftサーバーが起動しているか確認
  - ファイアウォールの設定を確認

- スクリプトが実行できない場合：
  - サーバーに接続されているか確認
  - ペットが選択されているか確認
  - コードに構文エラーがないか確認

## リンク

- [8x9craft2公式サイト](http://craft2.8x9.jp/ja/)
- [8x9craft2 APIリファレンス](http://wiki.craft2.8x9.jp/wiki/Category:APIs)
- [8x9.jp](http://8x9.jp/)
