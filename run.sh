#!/bin/bash
# run.sh - Phase1.5 式文字列パース＆評価の実行スクリプト
#
# 使い方:
#   ./run.sh "1+2*3"
#   ./run.sh "sin(exp(x))"
#   ./run.sh "-(1+2)*3"
#
# このスクリプトは以下を自動で実行します:
#   1. mvn package でビルド
#   2. mvn exec:java で DemoParseEval を実行

set -e  # エラーが発生したら即座に終了

# 引数チェック
if [ $# -eq 0 ]; then
    echo "Usage: ./run.sh \"<expression>\""
    echo "Example: ./run.sh \"1+2*3\""
    exit 1
fi

EXPR="$1"

echo "Building..."
mvn -q -DskipTests package

echo "Running: $EXPR"
mvn -q exec:java \
    -Dexec.mainClass=io.DemoParseEval \
    -Dexec.args="$EXPR"

