package ast;

import java.util.List;

public class Pow implements Expr {
    private final Expr base;
    private final Expr exp;

    public Pow(List<Expr> args) {
        if (args.size() != 2) {
            throw new IllegalArgumentException("Pow needs 2 args");
        }
        this.base = args.get(0);
        this.exp = args.get(1);
    }

    @Override
    public double eval(double x) {
        return Math.pow(base.eval(x), exp.eval(x));
    }

    // JSON変換用のgetter
    public Expr getBase() { return base; }
    public Expr getExp() { return exp; }
}
