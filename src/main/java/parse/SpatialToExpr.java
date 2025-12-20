package parse;

import java.util.*;
import java.util.stream.Collectors;

public class SpatialToExpr {

    public static class Result {
        public final String expr;
        public final List<String> warnings;
        public Result(String expr, List<String> warnings) {
            this.expr = expr;
            this.warnings = warnings;
        }
    }

    public Result buildExprString(Detection det) {
        List<String> warnings = new ArrayList<>();

        // 1) スコア低いのを落とす（閾値は適宜）
        double scoreThreshold = 0.20;
        List<DetSymbol> filtered = det.symbols.stream()
                .filter(x -> x.score >= scoreThreshold)
                .collect(Collectors.toList());
        
        // 低スコアを捨てた場合の警告（種類別にカウント）
        List<DetSymbol> dropped = det.symbols.stream()
                .filter(x -> x.score < scoreThreshold)
                .collect(Collectors.toList());
        
        if (!dropped.isEmpty()) {
            int digits = 0, operators = 0, funcs = 0;
            for (DetSymbol sym : dropped) {
                String token = sym.token;
                if (isNumberLike(token)) {
                    digits++;
                } else if (isOperator(token)) {
                    operators++;
                } else if (isFunc(token)) {
                    funcs++;
                }
            }
            
            List<String> parts = new ArrayList<>();
            if (digits > 0) parts.add(String.format("dropped digits: %d", digits));
            if (operators > 0) parts.add(String.format("dropped operators: %d", operators));
            if (funcs > 0) parts.add(String.format("dropped funcs: %d", funcs));
            
            if (parts.isEmpty()) {
                // その他（括弧など）のみの場合
                warnings.add(String.format("dropped %d low-score symbol(s) (threshold=%.2f)", 
                        dropped.size(), scoreThreshold));
            } else {
                warnings.add(String.join(", ", parts) + String.format(" (threshold=%.2f)", scoreThreshold));
            }
        }

        if (filtered.isEmpty()) {
            warnings.add("no symbols after filtering");
            return new Result("", warnings);
        }

        List<DetSymbol> s = filtered;

        // 2) ざっくり "同一行" を仮定（Phase1は1行のみ）
        //    将来: cyでクラスタリングして複数行対応
        s.sort(Comparator.comparingDouble(a -> a.box.cx()));

        // 3) トークン列にしつつ、べき(右上)をまとめる
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.size()) {
            DetSymbol cur = s.get(i);

            // 関数は "sin" 等のトークンが来る想定： sin (...) の形にする
            // （モデルが 's','i','n' 分割なら、ここで連結ルール追加が必要）
            tokens.add(cur.token);

            // exponent判定：次が "右上に小さい塊" なら ^( ... ) を挿入
            if (i + 1 < s.size()) {
                DetSymbol nxt = s.get(i + 1);
                if (looksLikeSuperscript(cur, nxt)) {
                    // superscript は "連続する限り" まとめる（例: x^(12))
                    List<DetSymbol> sup = new ArrayList<>();
                    int j = i + 1;
                    while (j < s.size() && looksLikeSuperscript(cur, s.get(j))) {
                        sup.add(s.get(j));
                        j++;
                    }
                    
                    // 複数の exponent 候補があった場合の警告
                    if (sup.size() > 1) {
                        warnings.add(String.format("multiple exponent candidates detected (%d symbols), using all", 
                                sup.size()));
                    }
                    
                    sup.sort(Comparator.comparingDouble(a -> a.box.cx()));
                    String supStr = sup.stream().map(x -> x.token).collect(Collectors.joining());

                    tokens.add("^");
                    tokens.add("(");
                    tokens.add(supStr);
                    tokens.add(")");

                    i = j;
                    continue;
                }
            }

            i++;
        }

        // 4) 暗黙の掛け算を挿入（めちゃ大事）
        List<String> withMul = new ArrayList<>();
        for (int k = 0; k < tokens.size(); k++) {
            String a = tokens.get(k);
            withMul.add(a);
            if (k + 1 < tokens.size()) {
                String b = tokens.get(k + 1);
                if (needImplicitMul(a, b)) {
                    withMul.add("*");
                }
            }
        }

        // 5) 文字列化
        String expr = String.join("", withMul);

        // 6) ちょいデバッグしやすく
        if (unbalancedParen(expr)) warnings.add("unbalanced parentheses (maybe detection miss)");

        return new Result(expr, warnings);
    }

    private boolean looksLikeSuperscript(DetSymbol base, DetSymbol cand) {
        // 右側にあって、上にある（yが小さい）＋サイズが小さい → べき
        double dx = cand.box.cx() - base.box.cx();
        if (dx <= 0) return false;

        // base より "上" に中心があるか
        boolean above = cand.box.cy() < base.box.cy() - 0.15 * base.box.h();

        // だいたい小さめ
        boolean smaller = cand.box.h() < 0.85 * base.box.h();

        // base の右上近辺
        boolean near = cand.box.x1 > base.box.cx() && cand.box.x1 < base.box.x2 + 1.5 * base.box.w();

        return above && smaller && near;
    }

    private boolean isNumberLike(String t) {
        if (t == null || t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return false;
        return true;
    }

    private boolean isAtomEnd(String a) {
        // 直前が "値を終える" トークンなら true
        // 例: 2 x ) は後ろに値が来たら掛け算が必要
        return isNumberLike(a) || a.equals("x") || a.equals(")") ;
    }

    private boolean isAtomStart(String b) {
        // 次が "値を開始する" トークンなら true
        // 例: x 2 ( sin sqrt は前が値なら掛け算が必要
        return isNumberLike(b) || b.equals("x") || b.equals("(") || isFunc(b) || b.equals("sqrt");
    }

    private boolean isFunc(String t) {
        return t.equals("sin") || t.equals("cos") || t.equals("exp") || t.equals("log") || t.equals("sqrt");
    }

    private boolean isOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/") || t.equals("^");
    }

    private boolean needImplicitMul(String a, String b) {
        // 明示演算子の前後は不要
        if (a.equals("+") || a.equals("-") || a.equals("*") || a.equals("/") || a.equals("^")) return false;
        if (b.equals("+") || b.equals("-") || b.equals("*") || b.equals("/") || b.equals("^")) return false;

        // 例: "sin" "(" は掛け算じゃない（関数呼び出し）
        if (isFunc(a) && b.equals("(")) return false;
        if (a.equals("sqrt") && b.equals("(")) return false;

        // 例: ")" "(" → * を入れる
        return isAtomEnd(a) && isAtomStart(b);
    }

    private boolean unbalancedParen(String s) {
        int bal = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') bal++;
            if (c == ')') bal--;
            if (bal < 0) return true;
        }
        return bal != 0;
    }
}
