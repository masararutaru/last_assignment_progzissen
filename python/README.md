# Python学習環境

このディレクトリには、手書き数式認識モデルの学習・ONNX変換用のPythonコードを配置します。

## ディレクトリ構成

```
python/
├── README.md           # このファイル
├── requirements.txt    # Python依存関係
├── .gitignore         # Git除外設定
├── export_onnx.py     # ONNX書き出しスクリプト
├── train/             # 学習コード（今後追加）
│   └── model.py       # モデル定義（今後追加）
└── weights/           # 学習済み重み（Git管理しない）
    ├── .gitkeep       # ディレクトリをGit管理に含めるため
    └── manifest.json  # 重みファイルのメタデータ
```

## セットアップ

### 1. 仮想環境の作成（推奨）

```bash
cd python
python -m venv venv

# Windows
venv\Scripts\activate

# Linux/Mac
source venv/bin/activate
```

### 2. 依存関係のインストール

```bash
pip install -r requirements.txt
```

### 3. 学習済み重みの配置

学習済みモデルの重みファイル（`.pt` または `.pth`）を `weights/` ディレクトリに配置してください。

**重要**: 重みファイルはGit管理しません（`.gitignore`で除外）。代わりに `weights/manifest.json` にメタデータを記録してください。

## 重みファイルの管理

### Git管理の方針

- ✅ **Gitに入れる**: Pythonコード、設定ファイル、`manifest.json`
- ❌ **Gitに入れない**: 重みファイル（`.pt`, `.pth`）、ONNXファイル（大きい場合）

### manifest.json の記録内容

`weights/manifest.json` に以下の情報を記録してください：

```json
{
  "model_name": "math_recognizer_v1",
  "checkpoint_file": "model_epoch_50.pth",
  "sha256": "abc123...",
  "trained_date": "2024-01-15",
  "dataset_version": "v1.0",
  "input_size": [1, 3, 256, 256],
  "num_classes": 50,
  "test_accuracy": 0.95,
  "test_loss": 0.05,
  "description": "Phase 1用の基本モデル"
}
```

### 重みファイルの取得方法

他の環境で重みファイルが必要な場合：

1. `weights/manifest.json` を確認
2. 指定されたファイル名・SHA256を確認
3. 共有ストレージや提出物として別途提供

## ONNXへの書き出し

### 基本的な使い方

```bash
python export_onnx.py \
    --checkpoint weights/model_epoch_50.pth \
    --output ../assets/model.onnx \
    --verify
```

### オプション

- `--checkpoint`: 学習済み重みファイルのパス（必須）
- `--output`: 出力ONNXファイルのパス（デフォルト: `../assets/model.onnx`）
- `--input-size`: 入力テンソルのサイズ（デフォルト: `1 3 256 256`）
- `--opset`: ONNX opsetバージョン（デフォルト: 17）
- `--verify`: 書き出し後にONNXファイルを検証

### ONNX化の注意点

1. **固定サイズの入出力**: 可変長は避け、固定サイズ（例: `[N, 3, 256, 256]`）を想定
2. **ONNX対応演算のみ**: PyTorchの全機能がONNXでサポートされているわけではない
3. **後処理はJava側で**: NMSやTopKなどの後処理はモデル内部に入れず、Java側で実装
4. **出力形式**: 固定長の候補（例: `boxes: [N, 4]`, `scores: [N]`, `class_ids: [N]`）を出力

## モデル設計の推奨事項

### 出力形式

モデルは以下のような固定長の出力を返すことを推奨：

```python
# 出力例
boxes: torch.Tensor      # [N, 4] - バウンディングボックス
scores: torch.Tensor     # [N] - 信頼度スコア
class_ids: torch.Tensor  # [N] - クラスID
```

`N` は固定値（例: 200）とし、スコアが低い候補は後処理で除外。

### 後処理の分離

以下の処理は **Java側で実装** することを推奨：

- スコア閾値によるフィルタリング（例: `score > 0.2`）
- 上位M件の選択（例: Top-100）
- 重複除去（NMSなど）

これにより、ONNX化が安定し、Java側での制御も容易になります。

## 開発の流れ

1. **モデル定義**: `train/model.py` にモデルを定義
2. **学習**: 学習スクリプトを実行（今後追加）
3. **検証**: Pythonで推論を確認
4. **ONNX書き出し**: `export_onnx.py` でONNXに変換
5. **ONNX検証**: `--verify` オプションで動作確認
6. **Java統合**: `assets/model.onnx` をJava側で読み込む

## トラブルシューティング

### ONNX書き出しエラー

- **エラー**: "Unsupported operator: XXX"
  - **解決**: その演算をONNX対応のものに置き換える（例: カスタム演算 → 標準演算）

- **エラー**: "Dynamic axes not supported"
  - **解決**: 固定サイズの入出力に変更する

### 重みファイルが見つからない

- `weights/manifest.json` を確認
- ファイル名とパスが正しいか確認
- SHA256が一致するか確認（改ざんチェック）

## 参考リンク

- [PyTorch ONNX Export](https://pytorch.org/docs/stable/onnx.html)
- [ONNX Runtime](https://onnxruntime.ai/)
- [ONNX Operators](https://github.com/onnx/onnx/blob/main/docs/Operators.md)
