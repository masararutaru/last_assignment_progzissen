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
            
            // 極限（簡易実装：直接代入）
            case "limit":
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Func limit needs 1 arg");
                }
                // 簡易実装：極限は直接代入で近似
                return args.get(0).eval(x);
            
            default:
                throw new IllegalArgumentException("Unknown func: " + name);
        }
    }

    // JSON変換用のgetter
    public String getName() { return name; }
    public List<Expr> getArgs() { return args; }
}
