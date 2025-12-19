# AST/JSON Spec v1

## Root 構造（必須）

```json
{
  "version": 1,
  "expr": { ... }
}
```

- **version**: `1` 固定（必須）
- **expr**: Exprノード（必須）
- **source**: オプション（メタ情報、未実装）

---

## Expr ノード（Phase 1）

すべてのExprノードは **`type` フィールドを必須**とする。

### Num（数値）

```json
{ "type": "Num", "value": number }
```

- **type**: `"Num"` 固定
- **value**: `number`（必須、double型）

### Sym（変数）

```json
{ "type": "Sym", "name": "x" }
```

- **type**: `"Sym"` 固定
- **name**: `string`（必須、Phase1は `"x"` のみ）

### Add（加算）

```json
{ "type": "Add", "args": [Expr, Expr, ...] }
```

- **type**: `"Add"` 固定
- **args**: `Expr[]`（必須、**2個以上**）

### Mul（乗算）

```json
{ "type": "Mul", "args": [Expr, Expr, ...] }
```

- **type**: `"Mul"` 固定
- **args**: `Expr[]`（必須、**2個以上**）

### Div（除算）

```json
{ "type": "Div", "args": [Expr, Expr] }
```

- **type**: `"Div"` 固定
- **args**: `Expr[]`（必須、**2個固定**）
  - `args[0]`: 分子（numerator）
  - `args[1]`: 分母（denominator）

### Pow（べき乗）

```json
{ "type": "Pow", "args": [Expr, Expr] }
```

- **type**: `"Pow"` 固定
- **args**: `Expr[]`（必須、**2個固定**）
  - `args[0]`: 底（base）
  - `args[1]`: 指数（exponent）

### Func（関数）

```json
{ "type": "Func", "name": "sin", "args": [Expr] }
```

- **type**: `"Func"` 固定
- **name**: `string`（必須、Phase1対応: `"sqrt"`, `"sin"`, `"cos"`, `"exp"`, `"log"`）
- **args**: `Expr[]`（必須、Phase1は**1個固定**）

---

## 実装上の制約

### 引数個数（arity）チェック

- **Add/Mul**: `args.length >= 2` でなければ例外
- **Div/Pow**: `args.length == 2` でなければ例外（`"Div needs 2 args"` など）
- **Func**: Phase1は `args.length == 1` でなければ例外（`"Func needs 1 arg: <name>"` など）

### 関数名の検証

- **Func**: `name` が未対応の場合は例外（`"Unknown func: <name>"` など）
- Phase1対応関数: `sqrt`, `sin`, `cos`, `exp`, `log`

### 正規化ルール

- **Add/Mul**: `args` はフラット化可（`Add(Add(a,b),c)` → `Add(a,b,c)`）
- **Add/Mul**: 2個未満は許可しない（正規化しやすい）

### エラーメッセージ

GUI表示用に具体的なメッセージを返す：
- `"Div needs 2 args"`
- `"Func needs 1 arg: sin"`
- `"Unknown func: laplace"`
