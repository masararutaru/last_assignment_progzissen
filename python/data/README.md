# データセット管理

このディレクトリには、手書き数式認識モデルの学習に使用するデータセットを配置します。

## ディレクトリ構成

```
python/data/
├── README.md              # このファイル
├── config/                 # 設定ファイル（Git管理する）
│   ├── __init__.py
│   └── classes.py         # クラス定義（19クラス以上に拡張可能）
├── preprocess/             # 前処理スクリプト（Git管理する）
│   ├── __init__.py
│   ├── kaggle_labels.py   # Kaggleデータのラベル生成
│   ├── prepare.py          # 汎用前処理スクリプト
│   ├── download.py         # データセット取得スクリプト
│   ├── inspect_data.py     # データ構造確認
│   └── view_labels.py      # ラベル表示
├── raw/                    # 生データ（Git管理しない）
│   ├── aidapearson_ocr/   # Kaggle OCRデータセット
│   ├── aida/               # Aida Calculus Math Handwriting Dataset
│   └── mathwriting/        # MathWriting Dataset
├── processed/              # 前処理済みデータ（Git管理しない）
│   ├── train/              # 学習用
│   │   ├── images/         # 画像ファイル（必要に応じて）
│   │   └── labels/         # YOLO形式のラベル（.txt）
│   └── val/                # 検証用
│       ├── images/
│       └── labels/
└── custom/                 # 自前データ（Javaアプリで描いた画像）
    ├── images/             # 画像ファイル
    └── labels/             # YOLO形式のラベル（.txt）
```

## 検出対象クラス（19クラス、拡張可能）

### クラス定義

クラス定義は `config/classes.py` で管理されています。19クラス以上に拡張する場合は、このファイルを編集してください。

```python
# config/classes.py
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',  # 数字（10クラス）
    '+', '-', '*', '/', '=',                            # 演算子（5クラス）
    'x',                                                 # 変数（1クラス）
    '(', ')'                                             # 括弧（2クラス）
]

# クラスIDマッピング（YOLO形式）
CLASS_TO_ID = {cls: idx for idx, cls in enumerate(CLASSES)}
```

### クラスを拡張する場合

1. `config/classes.py` の `CLASSES` リストに新しいクラスを追加
2. 必要に応じて `latex_to_class()` 関数にLaTeX記号のマッピングを追加
3. 前処理スクリプトを再実行

**例**: クラス数を20に増やす場合
```python
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    '+', '-', '*', '/', '=',
    'x',
    '(', ')',
    'y'  # 新しいクラスを追加
]
```

### YOLO形式のラベル

各画像に対応する `.txt` ファイルに、以下の形式でラベルを記録：

```
class_id center_x center_y width height
```

- `class_id`: クラスID（0-18）
- `center_x`, `center_y`: バウンディングボックスの中心座標（正規化済み、0.0-1.0）
- `width`, `height`: バウンディングボックスの幅・高さ（正規化済み、0.0-1.0）

例：
```
5 0.5 0.3 0.1 0.15
10 0.7 0.3 0.08 0.12
```

## データセット取得方法

### 1. Aida Calculus Math Handwriting Dataset

**取得方法**（要確認）:
- 公式サイトまたは論文のリンクからダウンロード
- または `python data/download.py --dataset aida` で自動取得（実装予定）

**前処理**:
```bash
python data/prepare.py --source raw/aida --target processed/train --classes 19
```

### 2. MathWriting Dataset

**取得方法**（要確認）:
- Google Researchのリポジトリから取得
- または `python data/download.py --dataset mathwriting` で自動取得（実装予定）

**前処理**:
```bash
python data/prepare.py --source raw/mathwriting --target processed/train --classes 19
```

### 3. 自前データの作成

Javaアプリで実際に描いた画像を `custom/images/` に配置し、記号単位でBBoxを付与。

**ラベル付けツール**:
- LabelImg（推奨）: https://github.com/heartexlabs/labelImg
- または手動でYOLO形式の `.txt` ファイルを作成

**配置例**:
```
custom/
├── images/
│   ├── sample_001.png
│   ├── sample_002.png
│   └── ...
└── labels/
    ├── sample_001.txt
    ├── sample_002.txt
    └── ...
```

## データセット前処理

### 19クラス抽出

公開データセットから19クラス分だけ抽出：

```bash
python data/prepare.py \
    --source raw/aida \
    --target processed/train \
    --classes 19 \
    --split train \
    --min-instances 10
```

**オプション**:
- `--source`: ソースデータセットのパス
- `--target`: 出力先のパス
- `--classes`: 抽出するクラス数（19）
- `--split`: データセットの分割（train/val/test）
- `--min-instances`: クラスあたりの最小インスタンス数

### データセット統計の確認

```bash
python data/prepare.py --source processed/train --stats
```

各クラスのインスタンス数を表示します。

## データセットの使用

学習スクリプトで使用：

```bash
python train/train.py \
    --data processed/train \
    --epochs 100 \
    --batch-size 16
```

## Kaggle OCRデータセットの処理

### ラベルファイルの生成（画像はコピーしない）

Kaggle OCRデータセットからラベルファイル（YOLO形式）のみを生成します。
画像はコピーせず、ラベルファイル（.txt）のみを生成するため、ディスク使用量を最小限に抑えられます。

**Docker環境での実行方法**:

```bash
# Dockerコンテナを起動
docker-compose up -d python

# コンテナに入る
docker exec -it handwritten-math-python bash

# ラベルファイルを生成（学習用）
cd /app/python/data
python preprocess/kaggle_labels.py \
    --source raw/aidapearson_ocr \
    --target processed \
    --split train

# ラベルファイルを生成（検証用、必要に応じて）
python preprocess/kaggle_labels.py \
    --source raw/aidapearson_ocr \
    --target processed \
    --split val
```

**オプション**:
- `--source`: Kaggleデータのソースディレクトリ（例: `raw/aidapearson_ocr`）
- `--target`: 出力先ディレクトリ（例: `processed`）
- `--split`: データセットの分割（`train`, `val`, `test`）

**出力**:
- `processed/{split}/labels/{uuid}.txt`: YOLO形式のラベルファイル

**注意**:
- 画像ファイルは生成されません（ラベルファイルのみ）
- `config/classes.py` で定義されたクラスに該当する文字がないサンプルはスキップされます
- クラス定義を変更した場合は、前処理スクリプトを再実行してください

## 注意事項

- **生データ（`raw/`）はGit管理しない**（`.gitignore`で除外）
- **前処理済みデータ（`processed/`）も大きい場合はGit管理しない**
- **設定ファイル（`config/`）と前処理スクリプト（`preprocess/`）はGit管理する**
- **自前データ（`custom/`）は60〜200枚程度を想定**
- データセットのライセンスを確認してから使用すること

## 前処理スクリプトの使い方

### データ構造の確認

```bash
# サンプルデータの構造を確認
python preprocess/inspect_data.py --json raw/aidapearson_ocr/batch_1/JSON/kaggle_data_1.json --sample 0

# 文字分布を分析
python preprocess/inspect_data.py --json raw/aidapearson_ocr/batch_1/JSON/kaggle_data_1.json --analyze
```

### ラベルの表示

```bash
# 正解ラベルを表示
python preprocess/view_labels.py --json raw/aidapearson_ocr/batch_1/JSON/kaggle_data_1.json --sample 0 --count 3
```

## 参考リンク

- [YOLOv5 Dataset Format](https://docs.ultralytics.com/datasets/)
- [Aida Calculus Math Handwriting Dataset](https://github.com/MaliParag/CalcForm)（要確認）
- [MathWriting Dataset](https://github.com/google-research-datasets/math-writing)（要確認）
