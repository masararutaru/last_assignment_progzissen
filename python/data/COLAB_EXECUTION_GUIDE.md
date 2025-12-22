# Colabでの実行ガイド

## 概要

`preprocess_kaggle_data.ipynb`をColabで上から順番に実行すれば、Google Driveに前処理が終わったデータが確実に生成されます。

## `kaggle_labels.py`の処理内容

### 処理の流れ

1. **JSONファイルの検索**
   - `source_dir`（例: `/content/drive/MyDrive/.../raw/aidapearson_ocr`）配下を再帰的に検索
   - `kaggle_data_*.json`という名前のJSONファイルを全て見つける

2. **各JSONファイルの処理**
   - JSONファイルを読み込み、各サンプル（`uuid`を持つエントリ）を処理
   - 各サンプルから以下を抽出：
     - `visible_latex_chars`: 可視文字のリスト
     - `xmins`, `xmaxs`, `ymins`, `ymaxs`: バウンディングボックスの座標（正規化済み、0.0-1.0）

3. **クラス変換**
   - LaTeX文字をクラスに変換（`latex_to_class`関数を使用）
   - 例: `+` → `+`, `\frac` → `/`, `\times` → `*`
   - 19クラス（0-9, +, -, *, /, =, x, (, )）に該当しない文字はスキップ

4. **YOLO形式への変換**
   - バウンディングボックスをYOLO形式に変換：
     - `(xmin, ymin, xmax, ymax)` → `(center_x, center_y, width, height)`
     - 全て正規化済み（0.0-1.0）

5. **ラベルファイルの生成**
   - 各サンプルの`uuid`をファイル名として、`.txt`ファイルを生成
   - 形式: `{uuid}.txt`
   - 内容: 各行が `class_id center_x center_y width height` の形式

6. **出力先**
   - `{target_dir}/{split}/labels/` に全てのラベルファイルを生成
   - 例: `processed/train/labels/` に全ての`.txt`ファイルが生成される

### 重要なポイント

- **画像はコピーしない**: ラベルファイル（`.txt`）のみを生成します
- **ディレクトリ構造は保持しない**: 全てのラベルファイルは`labels/`ディレクトリにフラットに配置されます
- **batch1~batch9やextraの構造は保持されない**: 全てのJSONファイルからラベルを抽出して、1つの`labels/`ディレクトリにまとめます

## パス設定の確認

### 現在の設定

```python
DATA_ROOT = "/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment"
OUT_ROOT = "/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment"
```

### 実際のデータ構造

```
/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment/
├── raw/
│   └── aidapearson_ocr/
│       ├── batch1/
│       │   ├── JSON/
│       │   │   └── kaggle_data_*.json
│       │   └── background_images/
│       ├── batch2/
│       │   └── ...
│       ├── ...
│       ├── batch9/
│       └── extra/
└── processed/  (生成される)
    └── train/
        └── labels/
            ├── {uuid1}.txt
            ├── {uuid2}.txt
            └── ...
```

### パスの確認方法

ノートブックのセル3（前処理スクリプト実行セル）に以下のコードを追加すると、パスが正しいか確認できます：

```python
# パスの確認（デバッグ用）
print("=" * 80)
print("パス確認")
print("=" * 80)
print(f"ソースディレクトリ: {source}")
print(f"  存在確認: {Path(source).exists()}")
if Path(source).exists():
    json_files = list(Path(source).rglob('kaggle_data_*.json'))
    print(f"  見つかったJSONファイル数: {len(json_files)}")
    if len(json_files) > 0:
        print(f"  例: {json_files[0]}")
print(f"出力先ディレクトリ: {target}")
print(f"  存在確認: {Path(target).exists()}")
print()
```

## 修正が必要な箇所

### 1. 結果確認セル（セル5）の修正

現在のコード：
```python
from config import CLASSES
```

修正後：
```python
import sys
from pathlib import Path

# パスを追加（リポジトリのpython/dataディレクトリ）
repo_path = Path('/content/last_assignment_progzissen')
sys.path.insert(0, str(repo_path / 'python' / 'data'))

from config import CLASSES
```

### 2. パス確認コードの追加（推奨）

セル3（前処理スクリプト実行セル）に、上記のパス確認コードを追加することを推奨します。

## 実行手順

1. **セル1**: Google Driveをマウント
2. **セル2**: Gitリポジトリをクローンし、環境変数を設定
3. **セル3**: 前処理スクリプトを実行（パス確認コードを追加推奨）
4. **セル5**: 結果確認（修正が必要）
5. **セル6**: 統計情報の確認（修正が必要）

## 注意事項

- **batch1~batch9やextraの構造は保持されない**: 全てのラベルファイルは`processed/train/labels/`にまとめられます
- **画像ファイルはコピーされない**: ラベルファイルのみが生成されます
- **JSONファイルの場所**: `batch1/JSON/kaggle_data_*.json`のような構造を想定しています
