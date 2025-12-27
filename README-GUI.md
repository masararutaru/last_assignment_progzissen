# GUIアプリケーション実行方法

## Linux/MacでDocker実行する場合（推奨）

### 1. プロジェクトをクローン
```bash
git clone <repository-url>
cd last_assignment
```

### 2. 重みファイルを配置
```bash
# assets/model.onnx を配置
# または、既存の重みファイルがある場合はコピー
cp /path/to/model.onnx assets/model.onnx
```

### 3. コンテナを起動
```bash
docker-compose up -d math
```

### 4. GUIアプリを実行
```bash
docker-compose exec math ./run-gui.sh
```

**Linuxの場合、X11が自動的にマウントされるため、GUIウィンドウが直接表示されます。**

## WindowsでDocker実行する場合

Windowsの場合はVNCサーバーを使用します。`docker-compose.yml`のコメントを切り替えてください。

### 1. docker-compose.ymlを編集
- Linux用の設定をコメントアウト
- Windows用VNC設定のコメントを外す

### 2. コンテナを起動
```bash
docker-compose up -d math
```

### 3. GUIアプリを実行
```bash
docker-compose exec math ./run-gui.sh
```

### 4. VNCクライアントで接続
- VNC Viewerをインストール
- `localhost:5900` に接続

## Windowsで直接実行する場合（Docker不要）

```bash
mvn compile
mvn exec:java -Dexec.mainClass="io.MathExpressionGUI"
```

## 使い方

1. Canvasにマウスで数式を描く
2. 「推論」ボタンを押す
3. 画像が `samples/images/` に保存され、推論結果が表示される

## 必要なファイル

- `assets/model.onnx` - ONNXモデルファイル（必須）

