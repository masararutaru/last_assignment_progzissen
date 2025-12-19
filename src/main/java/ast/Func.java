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
            case "exp":
            case "log":
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
                    case "exp":
                        return Math.exp(v);
                    case "log":
                        return Math.log(v); // natural log
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
            
            default:
                throw new IllegalArgumentException("Unknown func: " + name);
        }
    }
}
