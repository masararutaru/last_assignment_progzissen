package ast;

public class Num implements Expr {
    public final double value;
    public Num(double value) { this.value = value; }
    @Override public double eval(double x) { return value; }
}
