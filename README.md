# Handwritten Math Recognizer & Calculator  
（Java 最終課題 / 数式手書き認識＋計算）

---

## 1. プロジェクト概要

本プロジェクトは、**Javaで作成したお絵描きツール**に手書きされた数式を入力として、

1. **ONNX Runtime（Java）による推論**
2. **推論結果（bbox + クラス）から数式の構造（AST）を生成**
3. **ASTをJSONとして保存**
4. **ASTを用いた数値計算**
5. **計算結果や曖昧点をGUIに表示**

までを **End-to-End（E2E）で Java により実装**することを目的とする。

最終的には、

> **Java GUI + AI推論（ONNX） + AST(JSON) + 数値計算**

を統合したアプリケーションを完成させる。

---

## 2. 全体像（超シンプル）

【事前準備】（提出物外）
PyTorchで学習 → ONNX形式でモデルを書き出し

【提出物・本体】
Java
├─ お絵描きUI
├─ ONNX Runtimeで推論
├─ 推論結果 → AST生成
├─ AST → 数値評価
└─ 結果表示


### 重要な設計方針
- **提出物として評価されるのは Java プログラム**
- Python / PyTorch は **モデル作成のための補助工程**
- 推論時・実行時は **Javaのみで完結**

---

## 3. 最終課題としての想定ゴール（重要）

### 最低限の完成条件（Phase 1）

- Java GUI上で数式を手書き入力できる
- ONNXモデルを **Javaから直接推論**できる
- 推論結果をもとに **数式ASTを構築**できる
- ASTを **JSON形式で保存・再利用**できる
- `x=1` などを代入して **数値評価**できる
- 結果またはエラー（曖昧さ含む）をGUIに表示できる

> 微分・積分・行列・ラプラス変換は  
> **拡張可能な設計を用意するだけでOK**  
> まずは Phase 1 の安定動作を最優先とする。

---

## 4. 設計の中心概念：AST と JSON

### AST（Abstract Syntax Tree）とは
- 数式を **計算の意味構造（木構造）** として表したもの
- 例：`1 + 2 * 3`



- ASTがあれば、構造をたどるだけで計算できる

### JSONの役割
- ASTを **保存・受け渡しするための形式**
- Java内部ではASTはクラスとして扱い、
  - 保存・デバッグ・再利用時にJSONへ変換する

> **AST = 数式の意味そのもの**  
> **JSON = ASTを外に出すための箱**

---

## 5. フェーズ設計（段階的要件定義）

### Phase 0：仕様固定（最優先）
- ASTノード種類の確定
- ASTのJSONスキーマ v1 の固定
- 曖昧さ・エラーの表現方針を決める

### Phase 1：電卓レベル MVP
- 四則演算 / べき / 分数 / √
- sin / cos / exp / log
- 変数は `x` のみ
- **数値評価のみ**（記号計算なし）

### Phase 2：微分・定積分
- 数値微分 / 数値積分から開始
- 後に記号微分へ拡張

### Phase 3：行列・行列式
- 2×2, 3×3 行列
- 次元不一致の検出

### Phase 4：ラプラス変換
- 既知変換テーブル＋線形性

---

## 6. プロジェクト構成

```
project-root/
├─ README.md
├─ pom.xml # Maven設定ファイル
├─ Dockerfile
├─ docker-compose.yml
│
├─ assets/ # リソースファイル
│ ├─ model.onnx # 学習済みモデル（予定）
│ └─ labels.json # クラス定義（予定）
│
├─ schemas/
│ └─ expr.schema.v1.json # AST(JSON) スキーマ（固定仕様）
│
├─ specs/
│ └─ AST_JSON_SPEC_v1.md # AST仕様書
│
├─ samples/
│ └─ expr/ # AST(JSON) サンプル
│   ├─ add_mul.json
│   ├─ div.json
│   ├─ func_sin.json
│   └─ pow.json
│   ├─ expr/ # AST(JSON) サンプル
│   └─ detections/ # 推論結果（bbox + cls）サンプル（今後追加予定）
│   # 以下は今後追加予定
│   # ├─ images/ # 手書き入力サンプル
│   # └─ result/ # 計算結果サンプル
│
├─ tests/ # テストファイル（今後追加予定）
│ └─ phase1_tests.csv # Phase1 数値評価テスト（予定）
│
└─ src/
  └─ main/
    └─ java/
      ├─ ast/ # ASTクラス定義
      │   ├─ Expr.java
      │   ├─ Num.java
      │   ├─ Sym.java
      │   ├─ Add.java
      │   ├─ Mul.java
      │   ├─ Div.java
      │   ├─ Pow.java
      │   └─ Func.java
      │
      ├─ parse/ # 推論結果 → AST 生成
      │   ├─ Parser.java # 文字列パーサ（Phase 1.5）
      │   ├─ Tokenizer.java # トークナイザ
      │   ├─ Token.java # トークン定義
      │   ├─ BBox.java # バウンディングボックス
      │   ├─ DetSymbol.java # 検出シンボル
      │   ├─ Detection.java # 検出結果
      │   ├─ LabelMap.java # クラス名→トークンマッピング
      │   ├─ DetectionJson.java # 推論結果JSON読み込み
      │   ├─ SpatialToExpr.java # 空間配置→式文字列変換
      │   └─ DemoDetectionsToAst.java # デモ: 推論結果→AST
      │
      └─ io/ # JSON読み書き
          ├─ AstJson.java
          ├─ DemoLoadEval.java
          └─ DemoParseEval.java
          # 以下は今後追加予定
          # ├─ ui/ # お絵描きUI
          # └─ onnx/ # ONNX Runtime 呼び出し
```

# ※提出物外（事前準備）

```
python/ # PyTorch 学習コード（提出物外）
   ├─ train/ # PyTorch 学習コード
   └─ export_onnx.py # ONNX書き出し
```


---

## 7. やること（優先度順）

1. **AST & JSON仕様を固定**
2. **Javaで AST → 数値評価 をテストで固める**
3. PyTorchで認識モデルを学習（提出物外）
4. モデルを ONNX形式に変換
5. Javaで ONNX推論を実装
6. 推論結果（bbox）から AST を復元
7. GUIと統合してE2E動作確認

---

## 8. この設計の強み

- Java最終課題としての要件を完全に満たす
- AI部分を **ONNX経由で安全に分離**
- ASTを中心に据えることで拡張が容易
- JSONによるデバッグ・再現性が高い
- 大学レベル → 発展的内容まで自然に拡張可能

---

## 9. 今後の実装ステップ

- ASTクラス（Num / Add / Mul / Sym など）の実装
- JSON → AST → eval の最小実装
- テストコードによる数値評価の検証
- ONNX推論結果とAST生成の接続
- GUIでの結果・曖昧性表示

---

## 10. 実装ガイドライン

### JSON形式の統一

- **基本形**: `{"version": 1, "expr": ...}` で固定
- **Expr**: 必ず `type` フィールドを持つ
- **可変長**: `Add`/`Mul` は `args: [e1, e2, ...]` 形式
- **2引数**: `Div`/`Pow` も `args` 配列で2個固定（`left`/`right` や `base`/`exp` は使わない）
- **関数**: `Func` は `name` + `args`（Phase1は基本1引数）

### AstJson と ast の対応

- `AstJson.parseExpr` が受け付ける `type` と `ast/*` のクラスは必ず対応させる
- 片方だけ増やすと壊れるため、両方を同時に更新する

### 引数個数（arity）チェック

- `Div`/`Pow`: 2個でなければ例外を投げる
- `Func`: 未対応関数名・引数個数は例外を投げる（エラー表示の材料になる）
- 関数名 → 必要な引数数を表で管理（例：`sqrt`/`sin`/`cos`/`exp`/`log` は1個、`int` は3個）

### 新機能追加の手順

1. JSONサンプルを `samples/expr/` に追加
2. `tests/phase1_tests.csv` に1行追加
3. 必要なら `AstJson` に `type` 追加
4. 必要なら `ast/*` に `eval` 実装
5. `docker compose up --build` でテストが緑になるまで回す

### 新機能の表現方針

- **新機能はまず `Func(name, args)` で表す**
- 微分・積分・ラプラス・行列演算も基本は `Func` で統一：
  - `{"type":"Func","name":"diff","args":[expr]}`
  - `{"type":"Func","name":"int","args":[expr, a, b]}`（定積分）
  - `{"type":"Func","name":"laplace","args":[expr]}`
- `type` を増やすのは最後（本当に必要になったときだけ）

### その他の注意事項

- **JSONの値**: Phase1〜2は `double` 前提（`Num` は `double`、`Sym` は `x` のみ）
- **エラーメッセージ**: GUI表示用に具体的に（例：`"Func needs 1 arg: sin"`, `"Unknown func: laplace"`, `"Div needs 2 args"`）
- **正規化**: `Add`/`Mul` の `args` はフラット化可（`Add(Add(a,b),c)` → `Add(a,b,c)`）。`Add`/`Mul` は2個以上必須

---

## 11. 実行方法

### Dockerコンテナの起動

**重要**: Pythonコンテナは**提出物外**（学習・ONNX変換用）です。Javaの開発・テストだけなら不要です。

**Java環境だけ起動する場合（推奨）:**
```bash
docker compose up --build -d math
```

**両方起動する場合:**
```bash
docker compose up --build -d
```

**注意**: Pythonコンテナ（`python`）はPyTorchを含むため、**数GBのサイズ**になり、ビルドに**10-30分程度**かかります。また、**ディスク容量が十分にあることを確認**してください（最低5GB以上の空き容量推奨）。

これで以下のコンテナが起動します：
- `handwritten-math` (Java/Maven環境) - **必須**
- `handwritten-math-python` (Python/PyTorch環境) - **オプション**（学習・ONNX変換時のみ必要）

### コンテナに入る

**Java環境に入る:**
```bash
docker compose exec math bash
```

**Python環境に入る:**
```bash
docker compose exec python bash
```

### コンテナ内での実行

コンテナ内で以下のコマンドを実行できます：

#### ビルド(コード書き直したらbashないでこのコマンドを打つと反映される)

```bash
mvn -q -DskipTests package
```

#### Phase1: JSONファイルからASTを読み込んで評価

```bash
mvn -q exec:java -Dexec.mainClass=io.DemoLoadEval -Dexec.args="samples/expr/add_mul.json"
```

#### Phase1.5: 式文字列をパースして評価

**推奨：`run.sh` スクリプトを使用（簡単）**

```bash
./run.sh "1+2*3"
./run.sh "sin(exp(x))"
./run.sh "-(1+2)*3"
```

`run.sh` は自動でビルドと実行を行います。毎回 `mvn package` と `mvn exec:java` を手動で実行する必要がありません。

**従来の方法（直接Mavenコマンドを実行）**

```bash
mvn -q exec:java -Dexec.mainClass=io.DemoParseEval -Dexec.args="1+2*3"
mvn -q exec:java -Dexec.mainClass=io.DemoParseEval -Dexec.args="sin(exp(x))"
mvn -q exec:java -Dexec.mainClass=io.DemoParseEval -Dexec.args="-(1+2)*3"
```

#### Phase 1.5: AST ↔ JSON 往復変換のテスト

```bash
mvn -q exec:java -Dexec.mainClass=io.DemoJsonRoundTrip -Dexec.args="samples/expr/add_mul.json"
```

このデモは、JSON → AST → JSON の往復変換が正しく動作することを確認します。GUIで計算過程を表示する際に使用します。

### Python環境での作業

Pythonコンテナ内で以下を実行できます：

```bash
# Pythonコンテナに入る
docker compose exec python bash

# 仮想環境は不要（コンテナ内に直接インストール済み）
# 学習コードの実行（今後追加予定）
# python train/train.py

# ONNX書き出し
python python/export_onnx.py --checkpoint python/weights/model.pth --output assets/model.onnx --verify
```

### コンテナから出る・終了

```bash
exit                    # コンテナから出る
docker compose down      # コンテナを停止・削除
```

### Dockerのトラブルシューティング

#### Pythonコンテナのビルドが遅い・容量不足エラーが出る

**問題**: PyTorchは非常に大きなパッケージ（数GB）のため、ビルドに時間がかかり、ディスク容量を消費します。

**解決策**:
1. **Javaだけ使う場合**: Pythonコンテナは起動しない
   ```bash
   docker compose up --build -d math  # Javaだけ起動
   ```

2. **ディスク容量を確保**: 最低5GB以上の空き容量を確保してください
   ```bash
   # Dockerの使用容量を確認
   docker system df
   
   # 不要なイメージ・コンテナを削除
   docker system prune -a
   ```

3. **ビルドキャッシュを活用**: 一度ビルドしたら、次回はキャッシュが使われます

4. **Python環境はローカルで**: Dockerを使わず、ローカルにPython環境を構築する方法もあります
   ```bash
   cd python
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```

#### Javaコンテナだけ起動したい

```bash
# Javaコンテナだけ起動
docker compose up --build -d math

# Javaコンテナに入る
docker compose exec math bash
```

### run.sh スクリプトについて

`run.sh` は、Mavenコマンドをまとめて実行するためのシェルスクリプトです。

**なぜ便利か？**
- 毎回 `mvn package` と `mvn exec:java` を手動で実行する必要がない
- 1つのコマンドでビルドから実行まで自動化
- 引数として式文字列を渡すだけで実行できる

**仕組み：**
1. 引数として式文字列（例：`"1+2*3"`）を受け取る
2. `mvn -q -DskipTests package` でプロジェクトをビルド
3. `mvn exec:java` で `DemoParseEval` を実行し、引数の式を評価

**使い方：**
```bash
./run.sh "1+2*3"           # 式を評価
./run.sh "sin(x)"           # 関数を含む式もOK
./run.sh "-(1+2)*3"         # 単項マイナスもOK
```

## 12. Maven（mvn）の使い方

本プロジェクトでは、Javaのビルド・実行・依存関係管理に **Maven（mvn）** を使用する。  
Pythonにおける `pip + requirements.txt` に相当する役割を、`pom.xml` と Maven が担う。

---

### Mavenの役割

Mavenは以下を自動で行う：

- Javaライブラリ（依存関係）のダウンロード
- ソースコードのコンパイル
- 実行クラスの起動

本プロジェクトでは、`pom.xml` に記述された設定に基づいて動作する。

---

※ `src/main/java` 配下に Java ソースを配置することが必須。

---

### 依存関係の取得

```bash
mvn dependency:resolve
```



---

## 13. 進捗

### Phase 1: 完了
phase1まで完了

```bash
handwritten-math  | [OK] line=2 file=samples/expr/add_mul.json x=1.0 actual=7.000000000000000 expected=7.000000000000000 tol=1.00000e-09
handwritten-math  | [OK] line=3 file=samples/expr/div.json x=1.0 actual=3.000000000000000 expected=3.000000000000000 tol=1.00000e-09
handwritten-math  | [OK] line=4 file=samples/expr/pow.json x=2.0 actual=8.000000000000000 expected=8.000000000000000 tol=1.00000e-09 
handwritten-math  | [OK] line=5 file=samples/expr/func_sqrt.json x=0.0 actual=3.000000000000000 expected=3.000000000000000 tol=1.00000e-09                                                                                                                                
handwritten-math  | [OK] line=6 file=samples/expr/func_sin.json x=0.0 actual=0.000000000000000 expected=0.000000000000000 tol=1.00000e-09                                                                                                                                 
handwritten-math  | [OK] line=7 file=samples/expr/func_cos.json x=0.0 actual=1.000000000000000 expected=1.000000000000000 tol=1.00000e-09                                                                                                                                 
handwritten-math  | [OK] line=8 file=samples/expr/func_exp.json x=0.0 actual=2.718281828459045 expected=2.718281828459045 tol=1.00000e-09                                                                                                                                 
handwritten-math  | [OK] line=9 file=samples/expr/func_log.json x=0.0 actual=1.000000000000000 expected=1.000000000000000 tol=1.00000e-09                                                                                                                                 
handwritten-math  | 
handwritten-math  | RESULT: ok=8 ng=0 total=8
handwritten-math exited with code 0
```

### Phase 1.5: 推論結果からAST生成の基盤実装（進行中）

推論結果（bbox + クラス）から数式ASTを生成するための基盤クラスを実装しました。

#### 実装したクラス

1. **`parse/BBox.java`**
   - バウンディングボックスを表現するクラス
   - 幅・高さ・中心座標の計算メソッド
   - X/Y方向の重なり判定メソッド

2. **`parse/DetSymbol.java`**
   - 検出されたシンボル（記号）を表現するクラス
   - クラス名、正規化トークン、スコア、バウンディングボックスを保持

3. **`parse/Detection.java`**
   - 画像全体の検出結果を表現するクラス
   - 画像サイズと検出シンボルのリストを保持

4. **`parse/LabelMap.java`**
   - ONNXモデルのクラス名から式トークンへのマッピングを管理
   - 例: `"digit_3"` → `"3"`, `"plus"` → `"+"`, `"sin"` → `"sin"`
   - 既にトークン形式のクラス名もそのまま通す機能あり

5. **`parse/DetectionJson.java`**
   - JSON形式の推論結果を読み込むクラス
   - `org.json` を使用してJSONをパース
   - `LabelMap` を使ってクラス名をトークンに変換

6. **`parse/SpatialToExpr.java`**
   - 空間的な検出結果から式文字列を構築するコアクラス
   - 主な機能:
     - スコア閾値によるフィルタリング
     - X座標によるソート（同一行を仮定）
     - べき乗（右上の小さな記号）の検出と `^(...)` 形式への変換
     - 暗黙の掛け算の挿入（例: `2x` → `2*x`, `)(` → `)*(`）
     - 括弧のバランスチェック

7. **`parse/DemoDetectionsToAst.java`**
   - 推論結果JSONからASTを生成して評価するデモクラス
   - 使用例: `java parse.DemoDetectionsToAst samples/detections/example.json`

#### 現状

- ✅ 推論結果JSONの読み込み機能
- ✅ 空間的な配置から式文字列への変換ロジック
- ✅ 既存の `parse.Parser` との統合
- ✅ AST → JSON 変換機能（`AstJson.toJsonV1`）を実装
  - GUIでの計算過程表示やデバッグに有用
  - すべてのASTノードタイプ（Num, Sym, Add, Mul, Div, Pow, Func）に対応
  - JSON → AST → JSON の往復変換が可能
- ⚠️ ONNX推論との接続は未実装（次フェーズ）

#### 推論結果JSONフォーマット

推論結果JSONは以下の形式です：

```json
{
  "version": 1,
  "imageSize": {"w": 800, "h": 600},
  "detections": [
    {"cls": "1", "score": 0.98, "bbox": [ 80, 250, 120, 310 ]},
    {"cls": "+", "score": 0.95, "bbox": [140, 260, 170, 300 ]},
    {"cls": "2", "score": 0.97, "bbox": [190, 250, 230, 310 ]},
    {"cls": "*", "score": 0.93, "bbox": [260, 265, 290, 295 ]},
    {"cls": "3", "score": 0.99, "bbox": [310, 250, 350, 310 ]}
  ]
}
```

- `version`: バージョン番号（現在は1）
- `imageSize`: 画像サイズ（`w`: 幅, `h`: 高さ）
- `detections`: 検出結果の配列
  - `cls`: クラス名（例: `"1"`, `"+"`, `"digit_3"`, `"sin"` など）
  - `score`: 信頼度スコア（0.0〜1.0）
  - `bbox`: バウンディングボックス `[x1, y1, x2, y2]`（左上と右下の座標）

#### サンプルファイル

`samples/detections/` ディレクトリに以下のサンプルを配置：

- **`ex1_add_mul.json`** - 基本的な加算・乗算（`1+2*3`）
- **`ex2_2x_plus_3.json`** - 暗黙の掛け算（`2x+3`）
- **`ex3_paren_mul.json`** - 括弧と掛け算（`(1+2)*3`）
- **`ex4_x_pow_2_plus_1.json`** - べき乗（`x^2+1`）
- **`ex5_2_paren_x_plus_1.json`** - 暗黙の掛け算と括弧（`2(x+1)`）
- **`ex6_sin_x_plus_1.json`** - 関数（`sin(x)+1`）
- **`ex7_unknown_cls.json`** - 不明なクラス名の警告テスト
- **`ex8_low_score.json`** - 低スコアシンボルのフィルタリングテスト

これらのサンプルで `SpatialToExpr` の限界や警告機能を確認できます。

#### Warnings 機能

`SpatialToExpr` と `DetectionJson` は以下の警告を出力します：

- **低スコアシンボルのフィルタリング**: `dropped digits: N, dropped operators: M, dropped funcs: K (threshold=0.20)`（種類別にカウント）
- **不明なクラス名**: `unknown class label: 'xxx' (score=0.xx, bbox=[...]), skipped`
- **複数の exponent 候補**: `multiple exponent candidates detected (N symbols), using all`
- **括弧のバランス**: `unbalanced parentheses (maybe detection miss)`

出力順序は `[warn]` → `[expr]` → `[eval]` → `[ast-json]` で、「なぜこうなった？」が一発で分かる構成になっています。

これらの警告は GUI で「曖昧です」と表示する際に使用できます。

#### 次のステップ

1. ✅ `samples/detections/` ディレクトリに推論結果JSONのサンプルを配置済み
2. ✅ Warnings 機能を実装済み（種類別カウント、出力順序最適化）
3. ✅ `DemoDetectionsToAst` で動作確認済み（全サンプルで期待通りの結果を出力）
4. ONNX推論実装との統合
5. GUIとの統合

---

## 14. Object Detection方針（確定版）

### 0. 全体ゴール

Java製お絵描きアプリで描かれた数式画像から、

1. **Object Detection（記号単位）**
2. **Spatial parsing（位置関係）**
3. **AST生成 → 評価（計算）**

までを行う。

### 1. 検出対象クラス（確定）

最小構成として以下 **19クラス**：

- **数字**: `0`, `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`
- **演算子**: `+`, `-`, `*`, `/`, `=`
- **変数**: `x`
- **括弧**: `(`, `)`

まずはこの集合に固定。

> **注意**: `x` と `*` は混同しやすいが、現段階では別クラスで扱う。必要なら将来 AST 側で統合可能。

### 2. 検出モデルの基本方針

#### 採用モデル

**YOLOv5n（Ultralytics）**

#### 採用理由（根拠）

- **ONNX export が長年安定運用されている**
- **出力構造が単純**で Java側後処理（NMS実装）と相性が良い
- **nanoモデルは CPU推論でも現実的**
- **学習済み重み（COCO事前学習）が利用可能**

👉 **「詰まずに Java + ONNX + CPU で動かす」ことを最優先**

### 3. CPUで使えるか問題について（結論）

**問題なし**

- 事前学習済みかどうか ≠ CPUで動くか
- ONNXに変換 → **ONNX Runtime (CPU) で推論**
- 学習はGPU推奨だが、**推論はCPU前提**

### 4. データセット戦略（重要）

#### 基本思想

**拾えるデータでインスタンス数を稼ぐ** → **最後に自前データでドメイン適応**

#### 4.1 大量データ（主力）

以下のいずれか（または併用）：

**A. Aida Calculus Math Handwriting Dataset**
- 手書き数式画像
- 文字・記号単位のBBoxあり
- LaTeXラベル付き
- → 今回の object detection に最適

**B. MathWriting（Google Research, 合成側）**
- LaTeX → レンダリング → BBox生成
- 合成だが **大量に確保可能**
- 記号検出の事前学習に向く

👉 **19クラス分だけ抽出して使用**（全部使わない）

#### 4.2 自前データ（仕上げ）

**60〜200枚**

- Javaアプリで実際に描いた画像
- 記号単位でBBox付与

**目的**：
- 線の太さ
- アンチエイリアス
- 背景色
- 解像度

など **アプリ固有の見た目差を吸収**

### 5. 学習フロー（確定）

**段階的 fine-tuning（混ぜない）**

> **重要**: 混合学習はしない（自前データが埋もれるため）

```
[COCO事前学習済み YOLOv5n]
        ↓
[Aida / MathWriting 合成データ]
        ↓
[自前アプリ画像 60〜200枚]
```

各段階で fine-tune。最終段は数epochのみ（ドメイン合わせ）。

### 6. 推論パイプライン（Java側）

#### 全体流れ

```
Canvas画像
  ↓
前処理（Resize / Normalize）
  ↓
ONNX Runtime (CPU)
  ↓
Raw Detection Outputs
  ↓
NMS（← ここを SpatialToExpr.java に追加）
  ↓
Score Filtering
  ↓
Spatial Parsing → AST
```

### 7. NMSの扱い（重要・確定）

#### 方針

**Java側で NMS を実装する**

#### 理由

- YOLOv5 / v8 系 ONNX は **NMSなし生出力**
- Javaで制御したほうが：
  - IoU閾値調整しやすい
  - AST前処理と統合しやすい
  - デバッグが楽

#### 実装順（推奨）

1. **NMS**
2. **Score（confidence）フィルタリング**

👉 **`SpatialToExpr.java` のスコアフィルタリング前に NMS を入れる** は正しい設計判断

### 8. 推奨初期パラメータ

```java
confidence_threshold = 0.15   // Recall寄り
nms_iou_threshold     = 0.45
```

まずは**取りこぼしを減らす**。誤検出は AST 側・後段で吸収可能。

### 9. 評価指標（開発用）

- **mAP@0.5**（まずはこれ）
- Precision / Recall のバランス確認
- 最終的には **式全体の正解率**が主評価

### 10. 今後の拡張余地（今はやらない）

- クラス追加（√, 分数線, Σ, ∫ など）
- 構造検出（fraction line, superscript）
- 検出＋構造統合モデル

### 一文まとめ

**YOLOv5n（COCO事前学習）をベースに、Aida/MathWriting合成データで記号検出をfine-tuneし、最後に自前アプリ画像60〜200枚でドメイン適応。ONNXに変換し、Java（ONNX Runtime CPU）で推論。`SpatialToExpr.java`では NMS → スコアフィルタリング の順で後処理を行い、AST生成へ接続する。**

---

## 15. Python学習パイプライン（提出物外）

> **注意**: このセクションは **提出物外** の補助工程です。Java側の実装が優先されます。

### 学習環境のセットアップ

Pythonコンテナを使用する場合：

```bash
docker compose up --build -d python
docker compose exec python bash
```

### データセットの準備

#### ステップ1: 公開データセットの取得

**Aida Calculus Math Handwriting Dataset** または **MathWriting** から19クラス分のデータを抽出。

#### ステップ2: 自前データの作成

Javaアプリで実際に描いた画像60〜200枚を用意し、記号単位でBBoxを付与。

### 学習フロー

#### 段階1: COCO事前学習済みモデルの取得

```bash
# YOLOv5nの事前学習済み重みをダウンロード
# （Ultralytics公式から自動取得）
```

#### 段階2: 公開データセットでのFine-tuning

```bash
# Aida / MathWriting データで学習
python python/train/train.py --data aida_or_mathwriting --epochs 100
```

#### 段階3: 自前データでのドメイン適応

```bash
# 自前アプリ画像で最終調整
python python/train/train.py --data custom --epochs 10 --weights weights/aida_model.pth
```

### ONNXへの変換

学習完了後、ONNX形式に変換します。**Dockerコンテナ内で実行することを推奨**します。

#### 事前準備：ptファイルの配置

学習済みの`.pt`ファイルを`assets`ディレクトリに配置してください。

```bash
# 例: assets/best.pt または assets/model.pt
# assetsディレクトリが存在しない場合は作成してください
mkdir -p assets
# ptファイルを配置
cp /path/to/your/best.pt assets/best.pt
```

**注意**: `assets/*.pt`は`.gitignore`に含まれているため、gitにはコミットされません。

#### Docker環境での実行（推奨）

```bash
# Pythonコンテナを起動
docker compose up --build -d python

# コンテナに入る
docker compose exec python bash

# コンテナ内で実行（プロジェクトルートは /app）
cd /app

# assetsディレクトリ内のptファイルからONNXに変換
python python/export_onnx.py \
  --checkpoint assets/best.pt \
  --verify
```

**注意**: 
- `--output`を省略すると自動的に`assets/model.onnx`に出力されます
- プロジェクトルート（`pom.xml`があるディレクトリ）から実行してください

#### ローカル環境（Docker外）で実行する場合

```bash
# プロジェクトルートから実行
python python/export_onnx.py \
  --checkpoint assets/best.pt \
  --output assets/model.onnx \
  --verify
```

**前提条件**:
- `ultralytics>=8.0.0`がインストールされていること（Dockerコンテナ内には既にインストール済み）
- `assets`ディレクトリに`.pt`ファイルが配置されていること

### モデルの配置

変換したONNXモデルを `assets/model.onnx` に配置すると、Java側から使用可能になります。

**注意**: `assets/model.onnx`と`assets/*.pt`は`.gitignore`に含まれているため、gitにはコミットされません。チーム内で共有してください。

---

## 16. 実装タスク（今後の作業）

### 優先度: 高

1. **データセットの取得・準備**
   - Aida / MathWriting データセットのダウンロード
   - 19クラス分のデータ抽出スクリプト作成

2. **NMS実装（Java側）**
   - `SpatialToExpr.java` にNMS機能を追加
   - IoU閾値の調整機能

3. **ONNX推論実装（Java側）**
   - ONNX Runtime Java の統合
   - 前処理（Resize / Normalize）の実装

### 優先度: 中

4. **学習スクリプトの実装（Python側）**
   - YOLOv5nの学習パイプライン
   - データローダーの実装

5. **評価スクリプトの実装**
   - mAP@0.5の計算
   - Precision / Recall の可視化

### 優先度: 低

6. **GUIとの統合**
   - お絵描きUIからの画像入力
   - 推論結果のリアルタイム表示