package ast;

import java.util.List;

public class Mul implements Expr {
    public final List<Expr> args;
    public Mul(List<Expr> args) { this.args = args; }
    @Override public double eval(double x) {
        double p = 1;
        for (Expr e : args) p *= e.eval(x);
        return p;
    }
}
