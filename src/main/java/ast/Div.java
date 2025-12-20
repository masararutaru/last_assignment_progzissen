package ast;

import java.util.List;

public class Div implements Expr {
    private final Expr left;
    private final Expr right;

    public Div(List<Expr> args) {
        if (args.size() != 2) {
            throw new IllegalArgumentException("Div needs 2 args");
        }
        this.left = args.get(0);
        this.right = args.get(1);
    }

    @Override
    public double eval(double x) {
        double a = left.eval(x);
        double b = right.eval(x);
        if (b == 0.0) {
            throw new ArithmeticException("division by zero");
        }
        return a / b;
    }

    // JSON変換用のgetter
    public Expr getLeft() { return left; }
    public Expr getRight() { return right; }
}
