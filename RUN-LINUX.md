# LinuxでGUIアプリを実行する手順

## 前提条件

- DockerとDocker Composeがインストールされていること
- X11が動作していること（GUIデスクトップ環境）
- `assets/model.onnx` が配置されていること

## 実行手順

### 1. プロジェクトディレクトリに移動
```bash
cd last_assignment_progzissen
```

### 2. X11へのアクセス権限を設定（初回のみ）
```bash
xhost +local:docker
```

### 3. DISPLAY環境変数を確認・設定
```bash
# DISPLAY環境変数を確認
echo $DISPLAY

# 設定されていない場合
export DISPLAY=:0
```

### 4. Dockerコンテナを起動
```bash
docker-compose up -d math
```

### 5. GUIアプリを実行
```bash
docker-compose exec math ./run-gui.sh
```

GUIウィンドウが開きます！

## トラブルシューティング

### GUIウィンドウが表示されない場合

#### 1. DISPLAY環境変数を確認
```bash
# ホスト側で確認
echo $DISPLAY

# コンテナ内で確認
docker-compose exec math bash -c 'echo "DISPLAY=$DISPLAY"'
```

#### 2. X11ソケットの確認
```bash
ls -la /tmp/.X11-unix/
```

#### 3. X11へのアクセス権限を再設定
```bash
xhost +local:docker
docker-compose restart math
```

#### 4. エラーメッセージを確認
```bash
docker-compose exec math ./run-gui.sh
```

### よくあるエラー

#### `No X11 DISPLAY variable was set`
```bash
export DISPLAY=:0
docker-compose restart math
docker-compose exec math ./run-gui.sh
```

#### `Can't connect to X11 window server`
```bash
xhost +local:docker
docker-compose restart math
docker-compose exec math ./run-gui.sh
```

#### `Permission denied`
```bash
# dockerグループに所属しているか確認
groups | grep docker

# 所属していない場合
sudo usermod -aG docker $USER
# ログアウト・ログインし直す必要があります
```

## 使い方

1. GUIウィンドウが開いたら、Canvasにマウスで数式を描く
2. 「推論」ボタンを押す
3. 画像が `samples/images/` に保存され、推論結果が表示される

