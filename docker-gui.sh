#!/bin/bash
# Docker内でGUIアプリを実行するスクリプト（ホスト側から実行）

echo "=== Docker内でGUIアプリを起動 ==="
echo ""

# コンテナが起動しているか確認
if ! docker-compose ps | grep -q "handwritten-math.*Up"; then
    echo "コンテナを起動中..."
    docker-compose up -d math
    sleep 2
fi

# GUIアプリを実行
echo "GUIアプリケーションを起動します..."
echo ""

docker-compose exec math ./run-gui.sh

