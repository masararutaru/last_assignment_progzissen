package parse;

import java.util.*;

public class Tokenizer {

    // 1引数関数（Phase1）
    private static final Set<String> FUNCS = Set.of("sin","cos","exp","log","sqrt","neg");

    public static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int n = s.length();
        int i = 0;

        while (i < n) {
            char c = s.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            // number (support: 12, 12.34)
            if (Character.isDigit(c) || c == '.') {
                int j = i;
                boolean dot = false;
                while (j < n) {
                    char d = s.charAt(j);
                    if (Character.isDigit(d)) { j++; continue; }
                    if (d == '.' && !dot) { dot = true; j++; continue; }
                    break;
                }
                double v = Double.parseDouble(s.substring(i, j));
                out.add(Token.num(v));
                i = j;
                continue;
            }

            // identifier: x, sin, cos, ...
            if (Character.isLetter(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                String id = s.substring(i, j);

                if (FUNCS.contains(id)) out.add(Token.func(id));
                else out.add(Token.sym(id)); // 今は x 想定、将来拡張可

                i = j;
                continue;
            }

            // parentheses / comma
            if (c == '(') { out.add(Token.lp()); i++; continue; }
            if (c == ')') { out.add(Token.rp()); i++; continue; }
            if (c == ',') { out.add(Token.comma()); i++; continue; }

            // operators
            if ("+-*/^".indexOf(c) >= 0) { out.add(Token.op(String.valueOf(c))); i++; continue; }

            throw new IllegalArgumentException("Unexpected char: '" + c + "' at " + i);
        }

        return out;
    }
}

