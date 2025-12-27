# Linux環境でのセットアップ手順

## 1. プロジェクトのクローン

```bash
git clone <repository-url>
cd last_assignment
```

## 2. 重みファイルの配置

ONNXモデルファイルを `assets/` ディレクトリに配置します：

```bash
# 例：既存の重みファイルをコピー
cp /path/to/your/model.onnx assets/model.onnx

# または、学習済みモデルをダウンロード
# （プロジェクトの指示に従って）
```

## 3. Dockerで実行

### コンテナを起動
```bash
docker-compose up -d math
```

### GUIアプリを実行
```bash
docker-compose exec math ./run-gui.sh
```

**Linuxの場合、X11が自動的にマウントされるため、GUIウィンドウが直接表示されます。**

## 4. 動作確認

1. GUIウィンドウが開くことを確認
2. Canvasに数式を描く
3. 「推論」ボタンを押す
4. 結果が表示されることを確認

## トラブルシューティング

### DISPLAY環境変数が設定されていない場合

```bash
# 現在のDISPLAYを確認
echo $DISPLAY

# 設定されていない場合、設定する
export DISPLAY=:0
```

### X11ソケットが見つからない場合

```bash
# X11ソケットの存在確認
ls -la /tmp/.X11-unix/

# 存在しない場合、Xサーバーが起動していない可能性があります
```

### 権限エラーの場合

```bash
# X11へのアクセス権限を付与
xhost +local:docker
```

