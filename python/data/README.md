# データセット管理

このディレクトリには、手書き数式認識モデルの学習に使用するデータセットを配置します。

## ディレクトリ構成

```
python/data/
├── README.md              # このファイル
├── download.py            # データセット取得スクリプト
├── prepare.py             # データセット前処理スクリプト（19クラス抽出）
├── raw/                   # 生データ（Git管理しない）
│   ├── aida/              # Aida Calculus Math Handwriting Dataset
│   └── mathwriting/       # MathWriting Dataset
├── processed/             # 前処理済みデータ（19クラス抽出後）
│   ├── train/             # 学習用
│   │   ├── images/        # 画像ファイル
│   │   └── labels/        # YOLO形式のラベル（.txt）
│   └── val/               # 検証用
│       ├── images/
│       └── labels/
└── custom/                # 自前データ（Javaアプリで描いた画像）
    ├── images/            # 画像ファイル
    └── labels/            # YOLO形式のラベル（.txt）
```

## 検出対象クラス（19クラス）

### クラス定義

```python
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',  # 数字（10クラス）
    '+', '-', '*', '/', '=',                            # 演算子（5クラス）
    'x',                                                 # 変数（1クラス）
    '(', ')'                                             # 括弧（2クラス）
]

# クラスIDマッピング（YOLO形式）
CLASS_TO_ID = {cls: idx for idx, cls in enumerate(CLASSES)}
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

## 注意事項

- **生データ（`raw/`）はGit管理しない**（`.gitignore`で除外）
- **前処理済みデータ（`processed/`）も大きい場合はGit管理しない**
- **自前データ（`custom/`）は60〜200枚程度を想定**
- データセットのライセンスを確認してから使用すること

## 参考リンク

- [YOLOv5 Dataset Format](https://docs.ultralytics.com/datasets/)
- [Aida Calculus Math Handwriting Dataset](https://github.com/MaliParag/CalcForm)（要確認）
- [MathWriting Dataset](https://github.com/google-research-datasets/math-writing)（要確認）
