package parse;

public class Token {
    public enum Kind { NUM, SYM, FUNC, OP, LPAREN, RPAREN, COMMA }

    public final Kind kind;
    public final String text;   // 例: "+", "sin", "x"
    public final double number; // kind==NUM のときのみ有効

    private Token(Kind kind, String text, double number) {
        this.kind = kind;
        this.text = text;
        this.number = number;
    }

    public static Token num(double v) { return new Token(Kind.NUM, null, v); }
    public static Token sym(String s)  { return new Token(Kind.SYM, s, 0); }
    public static Token func(String s) { return new Token(Kind.FUNC, s, 0); }
    public static Token op(String s)   { return new Token(Kind.OP, s, 0); }
    public static Token lp()           { return new Token(Kind.LPAREN, "(", 0); }
    public static Token rp()           { return new Token(Kind.RPAREN, ")", 0); }
    public static Token comma()        { return new Token(Kind.COMMA, ",", 0); }

    @Override
    public String toString() {
        if (kind == Kind.NUM) return "NUM(" + number + ")";
        return kind + "(" + text + ")";
    }
}

