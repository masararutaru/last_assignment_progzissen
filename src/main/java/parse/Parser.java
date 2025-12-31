package parse;

import ast.*;
import java.util.*;

public class Parser {

    private static int prec(String op) {
        switch (op) {
            case "^": return 4;
            case "*":
            case "/": return 3;
            case "+":
            case "-": return 2;
            default: throw new IllegalArgumentException("Unknown op: " + op);
        }
    }

    private static boolean rightAssoc(String op) {
        return op.equals("^");
    }

    public static Expr parse(String input) {
        List<Token> toks = Tokenizer.tokenize(input);

        Deque<String> opStack = new ArrayDeque<>();
        Deque<Token> funcStack = new ArrayDeque<>();
        Deque<Expr> valStack = new ArrayDeque<>();

        // 単項マイナス判定用：直前が「値」かどうか
        boolean prevWasValue = false;

        for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);

            switch (t.kind) {
                case NUM:
                    valStack.push(new Num(t.number));
                    prevWasValue = true;
                    break;

                case SYM:
                    valStack.push(new Sym(t.text));
                    prevWasValue = true;
                    break;

                case FUNC:
                    funcStack.push(t);     // 関数名を積む
                    prevWasValue = false;  // 次は "(" を期待
                    break;

                case LPAREN:
                    opStack.push("(");
                    prevWasValue = false;
                    break;

                case COMMA:
                    // カンマはlimit関数の引数区切りとして処理
                    // カンマの前の式を引数として収集するため、演算子スタックを処理
                    while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                        String op = opStack.pop();
                        if (op.equals("u-")) {
                            applyUnaryNeg(valStack);
                        } else {
                            applyOp(op, valStack);
                        }
                    }
                    // カンマの前の式はvalStackに積まれているので、そのまま続ける
                    prevWasValue = false;
                    break;

                case RPAREN:
                    while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                        String op = opStack.pop();
                        if (op.equals("u-")) {
                            // 単項マイナスを適用
                            applyUnaryNeg(valStack);
                        } else {
                            // 二項演算子を適用
                            applyOp(op, valStack);
                        }
                    }
                    if (opStack.isEmpty() || !opStack.peek().equals("(")) {
                        throw new IllegalArgumentException("Mismatched ')'");
                    }
                    opStack.pop(); // pop "("

                    // 直前に関数が積まれていれば適用
                    if (!funcStack.isEmpty()) {
                        Token f = funcStack.pop();
                        // limit関数は複数引数をサポート
                        if (f.text.equals("limit")) {
                            // limit関数はカンマ区切りの引数を処理
                            // 引数は右から左に積まれているので、逆順に取得
                            List<Expr> args = new ArrayList<>();
                            // 括弧内の引数を取得（カンマで区切られている）
                            // 簡易実装：引数は既にvalStackに積まれていると仮定
                            // 実際には、カンマ区切りの引数を処理する必要がある
                            // ここでは、valStackから引数を取得
                            if (!valStack.isEmpty()) {
                                args.add(valStack.pop());
                            }
                            if (!valStack.isEmpty()) {
                                args.add(valStack.pop());
                            }
                            if (!valStack.isEmpty()) {
                                args.add(valStack.pop());
                            }
                            Collections.reverse(args); // 正しい順序に
                            valStack.push(new Func(f.text, args));
                        } else {
                            // その他の関数は1引数
                            Expr arg = valStack.pop();
                            valStack.push(new Func(f.text, List.of(arg)));
                        }
                        prevWasValue = true;
                    } else {
                        prevWasValue = true;
                    }
                    break;

                case OP: {
                    String op = t.text;

                    // 単項マイナス: 先頭 or 直前が値でない場合
                    if (op.equals("-") && !prevWasValue) {
                        // neg(...) として扱う：次の値を neg するために "u-" を積む
                        op = "u-";
                    }

                    while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                        String top = opStack.peek();
                        if (top.equals("u-")) {
                            // u- は最優先で先に適用
                            opStack.pop();
                            applyUnaryNeg(valStack);
                            continue;
                        }

                        int pTop = prec(top);
                        int pCur = op.equals("u-") ? 5 : prec(op);

                        if (pTop > pCur || (pTop == pCur && !rightAssoc(op))) {
                            applyOp(opStack.pop(), valStack);
                        } else break;
                    }

                    opStack.push(op);
                    prevWasValue = false;
                    break;
                }

                default:
                    throw new IllegalArgumentException("Unsupported token: " + t);
            }
        }

        while (!opStack.isEmpty()) {
            String op = opStack.pop();
            if (op.equals("(")) throw new IllegalArgumentException("Mismatched '('");
            if (op.equals("u-")) applyUnaryNeg(valStack);
            else applyOp(op, valStack);
        }

        if (valStack.size() != 1) {
            throw new IllegalArgumentException("Bad expression, remaining stack=" + valStack.size());
        }
        return valStack.pop();
    }

    private static void applyUnaryNeg(Deque<Expr> valStack) {
        Expr a = valStack.pop();
        valStack.push(new Func("neg", List.of(a)));
    }

    private static void applyOp(String op, Deque<Expr> valStack) {
        Expr b = valStack.pop();
        Expr a = valStack.pop();

        switch (op) {
            case "+":
                valStack.push(new Add(List.of(a, b)));
                return;
            case "-":
                // 二項引き算は Func("sub") に寄せる方針
                valStack.push(new Func("sub", List.of(a, b)));
                return;
            case "*":
                valStack.push(new Mul(List.of(a, b)));
                return;
            case "/":
                valStack.push(new Div(List.of(a, b)));
                return;
            case "^":
                valStack.push(new Pow(List.of(a, b)));
                return;
            default:
                throw new IllegalArgumentException("Unknown op: " + op);
        }
    }
}

