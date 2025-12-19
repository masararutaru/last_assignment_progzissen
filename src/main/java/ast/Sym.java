package ast;

public class Sym implements Expr {
    public final String name;
    public Sym(String name) { this.name = name; }
    @Override public double eval(double x) {
        if ("x".equals(name)) return x;
        throw new IllegalArgumentException("Unknown symbol: " + name);
    }
}
