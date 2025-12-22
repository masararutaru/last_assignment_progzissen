# Colab実行チェックリスト

## 実行前の確認事項

### 1. データの配置確認
- [ ] Google Driveに以下のパスにデータが存在するか確認
  - `/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment/raw/aidapearson_ocr/`
- [ ] ソースディレクトリ配下に`kaggle_data_*.json`ファイルが存在するか確認
- [ ] JSONファイルの構造が正しいか確認（`uuid`フィールドが存在するか）

### 2. リポジトリの確認
- [ ] GitHubリポジトリが公開されているか確認
- [ ] リポジトリURLが正しいか確認: `https://github.com/masararutaru/last_assignment_progzissen.git`

## 実行手順

### セル1: Google Driveマウント
```python
from google.colab import drive
drive.mount('/content/drive')
```
- **確認**: 認証が完了し、Driveがマウントされたか

### セル2: リポジトリクローンと環境変数設定
```python
%cd /content
!git clone https://github.com/masararutaru/last_assignment_progzissen.git
import os
os.environ["DATA_ROOT"] = "/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment"
os.environ["OUT_ROOT"] = "/content/drive/MyDrive/大学　講義/2年/後期/java_zissen/datasets/last_assignment"
```
- **確認**: 
  - リポジトリがクローンされたか
  - 環境変数が正しく設定されたか

### セル3: 前処理スクリプト実行
- **パス確認**: ソースディレクトリとJSONファイルの存在確認が表示される
- **実行**: 前処理スクリプトが実行される
- **確認事項**:
  - [ ] ソースディレクトリが存在するか（✓表示）
  - [ ] JSONファイルが見つかったか（件数が表示される）
  - [ ] 処理が正常に完了したか（エラーメッセージがない）
  - [ ] ラベルファイルが生成されたか（件数が表示される）

### セル5: 結果確認
- **確認事項**:
  - [ ] ラベルファイルが見つかったか
  - [ ] ラベルファイルの内容が正しいか（YOLO形式: `class_id center_x center_y width height`）

### セル6: 統計情報表示
- **確認事項**:
  - [ ] 各クラスのインスタンス数が表示されるか
  - [ ] 総インスタンス数が0より大きいか

### セル9: DataFrame表示（オプション）
- **確認事項**:
  - [ ] 統計情報がDataFrameで表示されるか

## 期待される出力構造

```
processed/
└── train/
    └── labels/
        ├── {uuid1}.txt
        ├── {uuid2}.txt
        └── ...
```

各`.txt`ファイルの形式:
```
class_id center_x center_y width height
class_id center_x center_y width height
...
```

例:
```
5 0.500000 0.300000 0.100000 0.150000
10 0.700000 0.300000 0.080000 0.120000
```

## トラブルシューティング

### エラー: JSONファイルが見つかりません
- **原因**: ソースディレクトリのパスが間違っている、またはJSONファイルが存在しない
- **対処**: 
  1. セル3のパス確認で表示されるソースディレクトリを確認
  2. Google Driveで実際のパスを確認
  3. JSONファイルの名前が`kaggle_data_*.json`の形式か確認

### エラー: ラベルファイルが1つも生成されませんでした
- **原因**: JSONファイルに有効なサンプルがない、または19クラスに該当する文字がない
- **対処**:
  1. JSONファイルの内容を確認
  2. サンプルに`uuid`フィールドが存在するか確認
  3. サンプルに19クラス（0-9, +, -, *, /, =, x, (, )）に該当する文字が含まれているか確認

### エラー: モジュールが見つかりません（`from config import CLASSES`）
- **原因**: `sys.path`が正しく設定されていない
- **対処**: セル5で`sys.path.insert(0, str(repo_path / 'python' / 'data'))`が実行されているか確認

### エラー: 出力ディレクトリの作成に失敗しました
- **原因**: 権限の問題、またはディスク容量不足
- **対処**:
  1. Google Driveの権限を確認
  2. ディスク容量を確認
  3. 出力先のパスを変更して再試行

## 拡張性について

現在の実装は以下の点で拡張性が高い設計になっています：

1. **クラス定義の拡張**: `config/classes.py`でクラスを追加するだけで、自動的に処理される
2. **LaTeX記号のマッピング**: `latex_to_class`関数にマッピングを追加するだけで対応可能
3. **エラーハンドリング**: 各処理段階でエラーが発生しても、他のファイルの処理を継続できる
4. **パス設定**: 環境変数で柔軟にパスを変更可能
5. **分割設定**: `train`, `val`, `test`を簡単に切り替え可能

## 確認完了チェックリスト

実行後、以下を確認してください：

- [ ] 全てのセルがエラーなく実行された
- [ ] ラベルファイルが期待される数生成された
- [ ] ラベルファイルの内容が正しい形式（YOLO形式）になっている
- [ ] 統計情報が表示され、各クラスのインスタンス数が0より大きい
- [ ] 出力先ディレクトリに`.txt`ファイルが存在する
