package ast;

public class Sym implements Expr {
    public final String name;
    public Sym(String name) { this.name = name; }
    @Override public double eval(double x) {
        // 変数は全てデフォルト値1.0を返す（簡易実装）
        // 将来的には変数ごとに値を設定できるように拡張
        if (name != null && name.length() == 1 && name.charAt(0) >= 'a' && name.charAt(0) <= 'z') {
            return 1.0; // 全ての変数に1.0を代入
        }
        throw new IllegalArgumentException("Unknown symbol: " + name);
    }
}
