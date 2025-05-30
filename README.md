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

### 1. Connect to Server

1. Click the hackCraft2 icon in the VSCode status bar
2. Enter the server address (e.g., "localhost:25570")
3. Enter your Minecraft player ID
4. Click "Connect"

### 2. Programming

You can program using the following languages:
- TypeScript (.ts)
- JavaScript (.js)
- Python (.py)

Example:
```javascript
// Draw a 4x4 square
for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 4; j++) {
        entity.forward()
    }
    entity.turnLeft()
}
```

### 3. Run Script

1. Select an entity in the hackCraft2 view
2. Click "Run Script" to execute your code
3. Watch the entity move in Minecraft!

## Features

- Multiple programming languages support (TypeScript, JavaScript, Python)
- Entity control (movement, rotation, etc.)
- 3D view display
- Server connection management
- Script execution/stop

## Troubleshooting

- If you can't connect:
  - Verify the server address and port number
  - Check if the Minecraft server is running
  - Check firewall settings

- If the script doesn't run:
  - Make sure an entity is selected
  - Verify server connection
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

### 1. サーバーへの接続

1. VSCodeのステータスバーにあるhackCraft2アイコンをクリック
2. サーバーアドレスを入力（例: "localhost:25570"）
3. MinecraftのプレイヤーIDを入力
4. "Connect"ボタンをクリック

### 2. プログラミング

以下の言語でプログラミングができます：
- TypeScript (.ts)
- JavaScript (.js)
- Python (.py)

例：
```javascript
// 4x4の正方形を描く
for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 4; j++) {
        entity.forward()
    }
    entity.turnLeft()
}
```

### 3. スクリプトの実行

1. hackCraft2ビューでエンティティを選択
2. "Run Script"ボタンをクリックして実行
3. Minecraftでエンティティの動きを確認！

## 機能

- 複数のプログラミング言語（TypeScript, JavaScript, Python）に対応
- エンティティの操作（移動、回転など）
- 3Dビューの表示
- サーバー接続状態の管理
- スクリプトの実行/停止

## トラブルシューティング

- 接続できない場合：
  - サーバーアドレスとポート番号が正しいか確認
  - Minecraftサーバーが起動しているか確認
  - ファイアウォールの設定を確認

- スクリプトが実行できない場合：
  - エンティティが選択されているか確認
  - サーバーに接続されているか確認
  - コードに構文エラーがないか確認

## リンク

- [8x9craft2公式サイト](http://craft2.8x9.jp/ja/)
- [8x9craft2 APIリファレンス](http://wiki.craft2.8x9.jp/wiki/Category:APIs)
- [8x9.jp](http://8x9.jp/)
