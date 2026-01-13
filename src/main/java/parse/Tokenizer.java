package parse;

import java.util.*;

public class Tokenizer {

    // 関数名リスト（拡張版）
    private static final Set<String> FUNCS = Set.of(
        "sin", "cos", "tan", "sec", "csc", "cot",  // 三角関数
        "ln", "log", "exp",                        // 対数・指数
        "sqrt", "abs",                             // その他関数
        "neg", "sub",                              // 単項・二項演算
        "diff", "limit"                            // 微分・極限
    );
    
    // 定数
    private static final Set<String> CONSTANTS = Set.of("π", "e", "∞");

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

                if (FUNCS.contains(id)) {
                    out.add(Token.func(id));
                } else if (id.equals("e") && CONSTANTS.contains("e")) {
                    // eを定数として扱う（記号として保持）
                    out.add(Token.sym("e"));
                } else {
                    out.add(Token.sym(id)); // 変数（a-z、ただしeは除く）
                }

                i = j;
                continue;
            }
            
            // 特殊文字（π, θ, ∞, →, √）
            if (c == 'π' || c == 'θ' || c == '∞') {
                String constName = String.valueOf(c);
                // 定数として記号として保持（数値に変換しない）
                out.add(Token.sym(constName));
                i++;
                continue;
            }
            
            if (c == '→') {
                // 矢印は演算子として扱う（簡易実装）
                out.add(Token.op("→"));
                i++;
                continue;
            }
            
            if (c == '√') {
                // ルート記号は関数として扱う
                out.add(Token.func("sqrt"));
                i++;
                continue;
            }

            // parentheses / comma / absolute value
            if (c == '(') { out.add(Token.lp()); i++; continue; }
            if (c == ')') { out.add(Token.rp()); i++; continue; }
            if (c == '|') { 
                // 絶対値記号（簡易実装：括弧として扱う）
                // 実際の処理はSpatialToExprでabs(...)に変換済み
                out.add(Token.rp()); // 簡易的に閉じ括弧として扱う
                i++; 
                continue; 
            }
            if (c == ',') { out.add(Token.comma()); i++; continue; }

            // operators
            if ("+-*/^".indexOf(c) >= 0) { out.add(Token.op(String.valueOf(c))); i++; continue; }

            throw new IllegalArgumentException("Unexpected char: '" + c + "' at " + i);
        }

        return out;
    }
}

