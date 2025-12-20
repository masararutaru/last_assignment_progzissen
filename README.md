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
│   # 以下は今後追加予定
│   # ├─ images/ # 手書き入力サンプル
│   # ├─ detections/ # 推論結果（bbox + cls）
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
      └─ io/ # JSON読み書き
          ├─ AstJson.java
          └─ DemoLoadEval.java
          # 以下は今後追加予定
          # ├─ ui/ # お絵描きUI
          # ├─ onnx/ # ONNX Runtime 呼び出し
          # ├─ parse/ # 推論結果 → AST 生成
          # └─ eval/ # 数値評価器

# ※提出物外（事前準備）
# python/ # PyTorch 学習コード（提出物外）
#   ├─ train/ # PyTorch 学習コード
#   └─ export_onnx.py # ONNX書き出し



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

```bash
docker compose up --build -d
```

### コンテナに入る

```bash
docker compose exec math bash
```

### コンテナ内での実行

コンテナ内で以下のコマンドを実行できます：

#### ビルド

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

### コンテナから出る・終了

```bash
exit                    # コンテナから出る
docker compose down      # コンテナを停止・削除
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