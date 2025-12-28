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
        // OnnxInferenceで既に0.15でフィルタリングされているため、ここではより低い閾値を使用
        double scoreThreshold = 0.10;  // 0.20から0.10に下げる
        List<DetSymbol> filtered = det.symbols.stream()
                .filter(x -> x.score >= scoreThreshold)
                .collect(Collectors.toList());
        
        // デバッグ情報: 検出されたシンボルとスコアを表示
        if (det.symbols.isEmpty()) {
            warnings.add("検出されたシンボルがありません");
            return new Result("", warnings);
        }
        
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
            if (digits > 0) parts.add(String.format("数字 %d個", digits));
            if (operators > 0) parts.add(String.format("演算子 %d個", operators));
            if (funcs > 0) parts.add(String.format("関数 %d個", funcs));
            
            if (parts.isEmpty()) {
                // その他（括弧など）のみの場合
                warnings.add(String.format("確率の低いシンボル %d個を除外しました (閾値=%.2f)", 
                        dropped.size(), scoreThreshold));
            } else {
                warnings.add(String.format("確率の低いシンボルを除外: %s (閾値=%.2f)", 
                        String.join(", ", parts), scoreThreshold));
            }
        }

        if (filtered.isEmpty()) {
            // デバッグ情報: 実際のスコアを表示
            if (!det.symbols.isEmpty()) {
                double minScore = det.symbols.stream().mapToDouble(s -> s.score).min().orElse(0.0);
                double maxScore = det.symbols.stream().mapToDouble(s -> s.score).max().orElse(0.0);
                double avgScore = det.symbols.stream().mapToDouble(s -> s.score).average().orElse(0.0);
                warnings.add(String.format("フィルタリング後にシンボルが残りませんでした (検出数=%d, スコア範囲=%.3f-%.3f, 平均=%.3f, 閾値=%.2f)", 
                        det.symbols.size(), minScore, maxScore, avgScore, scoreThreshold));
            } else {
                warnings.add("フィルタリング後にシンボルが残りませんでした");
            }
            return new Result("", warnings);
        }

        List<DetSymbol> s = filtered;

        // 2) ざっくり "同一行" を仮定（Phase1は1行のみ）
        //    将来: cyでクラスタリングして複数行対応
        s.sort(Comparator.comparingDouble(a -> a.box.cx()));

        // 3) トークン列にしつつ、べき(右上)をまとめる
        // トークンとDetSymbolの対応を保持（数字の連続判定のため）
        List<String> tokens = new ArrayList<>();
        List<DetSymbol> tokenSymbols = new ArrayList<>(); // 各トークンに対応するDetSymbol（null可）
        int i = 0;
        while (i < s.size()) {
            DetSymbol cur = s.get(i);

            // 関数は "sin" 等のトークンが来る想定： sin (...) の形にする
            // （モデルが 's','i','n' 分割なら、ここで連結ルール追加が必要）
            tokens.add(cur.token);
            tokenSymbols.add(cur);

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
                        warnings.add(String.format("複数の指数候補が検出されました (%d個のシンボル)、すべて使用します", 
                                sup.size()));
                    }
                    
                    sup.sort(Comparator.comparingDouble(a -> a.box.cx()));
                    String supStr = sup.stream().map(x -> x.token).collect(Collectors.joining());

                    tokens.add("^");
                    tokenSymbols.add(null);
                    tokens.add("(");
                    tokenSymbols.add(null);
                    tokens.add(supStr);
                    tokenSymbols.add(null); // 複数シンボルの合成なのでnull
                    tokens.add(")");
                    tokenSymbols.add(null);

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
                DetSymbol symA = tokenSymbols.get(k);
                DetSymbol symB = tokenSymbols.get(k + 1);
                if (needImplicitMul(a, b, symA, symB)) {
                    withMul.add("*");
                }
            }
        }

        // 4.5) 括弧の対応を修正（)が(に誤認識される問題に対処）
        List<String> correctedParens = fixParenMismatch(withMul, warnings);

        // 5) 文字列化
        String expr = String.join("", correctedParens);

        // 6) ちょいデバッグしやすく
        if (unbalancedParen(expr)) warnings.add("括弧の対応が取れていません（検出ミスの可能性があります）");

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

    private boolean needImplicitMul(String a, String b, DetSymbol symA, DetSymbol symB) {
        // 明示演算子の前後は不要
        if (a.equals("+") || a.equals("-") || a.equals("*") || a.equals("/") || a.equals("^")) return false;
        if (b.equals("+") || b.equals("-") || b.equals("*") || b.equals("/") || b.equals("^")) return false;

        // 例: "sin" "(" は掛け算じゃない（関数呼び出し）
        if (isFunc(a) && b.equals("(")) return false;
        if (a.equals("sqrt") && b.equals("(")) return false;

        // 数字同士の場合：連続している（近い位置にある）場合は掛け算を挿入しない
        if (isNumberLike(a) && isNumberLike(b)) {
            if (symA != null && symB != null) {
                // 2つの数字が近い位置にあるかチェック
                double distance = symB.box.x1 - symA.box.x2; // 右端から左端までの距離
                double avgWidth = (symA.box.w() + symB.box.w()) / 2.0;
                // 平均幅の0.5倍以内なら連続していると判断
                if (distance <= avgWidth * 0.5) {
                    return false; // 連続しているので掛け算を挿入しない
                }
            }
        }

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
    
    /**
     * 括弧の対応を修正
     * (が連続して認識された場合、後半の(を)に修正する
     * ただし、ネストされた括弧の場合はそのまま
     */
    private List<String> fixParenMismatch(List<String> tokens, List<String> warnings) {
        List<String> result = new ArrayList<>(tokens);
        
        // 括弧のバランスを計算
        int openCount = 0;
        int closeCount = 0;
        for (String token : result) {
            if (token.equals("(")) openCount++;
            if (token.equals(")")) closeCount++;
        }
        
        // 開き括弧が余っている場合のみ修正
        if (openCount > closeCount) {
            int excess = openCount - closeCount;
            
            // 左から右に走査して、連続する(の後半を)に修正
            int parenBalance = 0;  // 現在の括弧のバランス
            int fixedCount = 0;
            
            for (int i = 0; i < result.size() && fixedCount < excess; i++) {
                String token = result.get(i);
                
                if (token.equals("(")) {
                    parenBalance++;
                    
                    // 次のトークンも(で、かつ括弧のバランスが1以上（開き括弧が余っている）場合
                    if (i + 1 < result.size() && result.get(i + 1).equals("(") && parenBalance > 0) {
                        // 後半の(を)に修正
                        result.set(i + 1, ")");
                        fixedCount++;
                        parenBalance--;  // 修正したのでバランスを調整
                        warnings.add(String.format("括弧の対応を修正: 位置%dの(を)に変更", i + 1));
                    }
                } else if (token.equals(")")) {
                    parenBalance--;
                    if (parenBalance < 0) {
                        // 閉じ括弧が多すぎる場合は開き括弧に修正（逆パターン）
                        parenBalance = 0;
                    }
                }
            }
            
            // まだ余っている場合、右から左に走査して修正
            if (fixedCount < excess) {
                parenBalance = 0;
                for (int i = result.size() - 1; i >= 0 && fixedCount < excess; i--) {
                    String token = result.get(i);
                    
                    if (token.equals(")")) {
                        parenBalance++;
                    } else if (token.equals("(")) {
                        if (parenBalance == 0 && fixedCount < excess) {
                            // 対応する閉じ括弧がない開き括弧を閉じ括弧に修正
                            result.set(i, ")");
                            fixedCount++;
                            warnings.add(String.format("括弧の対応を修正: 位置%dの(を)に変更（右から）", i));
                        } else {
                            parenBalance--;
                        }
                    }
                }
            }
        }
        
        return result;
    }
}
