package ast;

import java.util.List;

public class Add implements Expr {
    public final List<Expr> args;
    public Add(List<Expr> args) { this.args = args; }
    @Override public double eval(double x) {
        double s = 0;
        for (Expr e : args) s += e.eval(x);
        return s;
    }
}
