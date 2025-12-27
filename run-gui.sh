#!/bin/bash
# GUIアプリケーションを実行するスクリプト

echo "=== 数式認識GUIアプリケーション起動 ==="
echo ""

# DISPLAY環境変数の確認
if [ -z "$DISPLAY" ]; then
    echo "警告: DISPLAY環境変数が設定されていません"
    echo "Linux/Macの場合、X11が利用可能か確認してください"
    echo "Windowsの場合、VNCサーバーが起動しているか確認してください"
fi

# コンパイル
echo "コンパイル中..."
mvn -q compile

if [ $? -ne 0 ]; then
    echo "エラー: コンパイルに失敗しました"
    exit 1
fi

echo "コンパイル完了"
echo ""

# クラスパスを構築
CLASSPATH="target/classes"
DEPENDENCIES=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)
if [ -n "$DEPENDENCIES" ]; then
    CLASSPATH="$CLASSPATH:$DEPENDENCIES"
fi

# GUIアプリを実行
echo "GUIアプリケーションを起動します..."
echo "DISPLAY=$DISPLAY で実行します"
echo ""

java -cp "$CLASSPATH" io.MathExpressionGUI

