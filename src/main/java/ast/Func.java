package ast;

import java.util.List;

public class Func implements Expr {
    private final String name;
    private final List<Expr> args;

    public Func(String name, List<Expr> args) {
        this.name = name;
        this.args = List.copyOf(args);
    }

    @Override
    public double eval(double x) {
        switch (name) {
            // 単引数関数
            case "sqrt":
            case "sin":
            case "cos":
            case "tan":
            case "sec":
            case "csc":
            case "cot":
            case "ln":
            case "log":
            case "exp":
            case "abs":
            case "neg":
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Func needs 1 arg: " + name);
                }
                double v = args.get(0).eval(x);
                switch (name) {
                    case "sqrt":
                        return Math.sqrt(v);
                    case "sin":
                        return Math.sin(v);
                    case "cos":
                        return Math.cos(v);
                    case "tan":
                        return Math.tan(v);
                    case "sec":
                        return 1.0 / Math.cos(v);
                    case "csc":
                        return 1.0 / Math.sin(v);
                    case "cot":
                        return 1.0 / Math.tan(v);
                    case "ln":
                    case "log":
                        return Math.log(v); // natural log
                    case "exp":
                        return Math.exp(v);
                    case "abs":
                        return Math.abs(v);
                    case "neg":
                        return -v;
                    default:
                        throw new IllegalArgumentException("Unknown func: " + name);
                }
            
            // 二引数関数
            case "sub":
                if (args.size() != 2) {
                    throw new IllegalArgumentException("Func needs 2 args: " + name);
                }
                return args.get(0).eval(x) - args.get(1).eval(x);
            
            // 微分（簡易実装：数値微分）
            case "diff":
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Func diff needs 1 arg (variable name)");
                }
                // 簡易実装：微分は後で拡張（現時点では0を返す）
                // TODO: シンボリック微分を実装
                return 0.0;
            
            // 極限（lim_{variable→limitValue} expression）
            case "limit":
                if (args.size() < 2 || args.size() > 3) {
                    throw new IllegalArgumentException("Func limit needs 2 or 3 args: [variable], limitValue, [expression]");
                }
                
                // 引数の処理
                // args[0]: 変数名（オプション、文字列として扱う必要があるが、Exprなので簡易的に無視）
                // args[1]: 収束値（またはargs[0]が収束値の場合）
                // args[2]: 式（オプション）
                
                double limitValue;
                Expr expression;
                
                if (args.size() == 2) {
                    // limit(limitValue, expression) または limit(variable, limitValue)
                    // 簡易実装：最初の引数を収束値として扱う
                    limitValue = args.get(0).eval(x);
                    expression = args.get(1);
                } else {
                    // limit(variable, limitValue, expression)
                    limitValue = args.get(1).eval(x);
                    expression = args.get(2);
                }
                
                // 無限大への収束をチェック
                boolean isInfinity = Double.isInfinite(limitValue);
                boolean isZero = !isInfinity && Math.abs(limitValue) < 1e-10;
                
                // 式を評価
                if (expression != null) {
                    // 式がある場合、変数を収束値に近づけて評価
                    if (isInfinity) {
                        // 無限大への収束：大きな値で評価
                        double largeValue = limitValue > 0 ? 1e10 : -1e10;
                        return expression.eval(largeValue);
                    } else if (isZero) {
                        // 0への収束：小さな値で評価
                        double smallValue = limitValue >= 0 ? 1e-10 : -1e-10;
                        return expression.eval(smallValue);
                    } else {
                        // 通常の収束値：直接代入
                        return expression.eval(limitValue);
                    }
                } else {
                    // 式がない場合、収束値をそのまま返す
                    return limitValue;
                }
            
            default:
                throw new IllegalArgumentException("Unknown func: " + name);
        }
    }

    // JSON変換用のgetter
    public String getName() { return name; }
    public List<Expr> getArgs() { return args; }
}
