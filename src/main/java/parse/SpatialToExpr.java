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

        // 3) 関数名のマージ（複数文字の関数名を1つのトークンにまとめる）
        // 例: 's','i','n' → "sin", 'l','o','g' → "log"
        List<DetSymbol> merged = mergeFunctionNames(s, warnings);
        
        // 4) トークン列にしつつ、べき(右上)をまとめる
        // トークンとDetSymbolの対応を保持（数字の連続判定のため）
        List<String> tokens = new ArrayList<>();
        List<DetSymbol> tokenSymbols = new ArrayList<>(); // 各トークンに対応するDetSymbol（null可）
        int i = 0;
        while (i < merged.size()) {
            DetSymbol cur = merged.get(i);

            tokens.add(cur.token);
            tokenSymbols.add(cur);

            // exponent判定：次が "右上に小さい塊" なら ^( ... ) を挿入
            if (i + 1 < merged.size()) {
                DetSymbol nxt = merged.get(i + 1);
                if (looksLikeSuperscript(cur, nxt)) {
                    // superscript は "連続する限り" まとめる（例: x^(12))
                    List<DetSymbol> sup = new ArrayList<>();
                    int j = i + 1;
                    while (j < merged.size() && looksLikeSuperscript(cur, merged.get(j))) {
                        sup.add(merged.get(j));
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
        
        // 5) ルート記号の処理（√記号の直後の式を括弧で囲む）
        tokens = processSqrtSymbols(tokens, tokenSymbols, warnings);
        
        // 6) 絶対値の処理（|...|をabs(...)に変換）
        tokens = processAbsoluteValue(tokens, warnings);
        
        // 7) 分数の処理（分数線の上下を検出して(numerator)/(denominator)に変換）
        tokens = processFractions(tokens, tokenSymbols, warnings);
        
        // 8) 微分演算子の処理（d/dx構造を検出）
        tokens = processDerivatives(tokens, warnings);
        
        // 9) 極限の処理（lim_{x→a}構造を検出）
        tokens = processLimits(tokens, warnings);

        // 10) 括弧の対応を修正（)が(に誤認識される問題に対処）
        // 暗黙の掛け算を挿入する前に括弧の対応を修正する必要がある
        List<String> correctedParens = fixParenMismatch(tokens, warnings);

        // 11) 暗黙の掛け算を挿入（めちゃ大事）
        List<String> withMul = new ArrayList<>();
        for (int k = 0; k < correctedParens.size(); k++) {
            String a = correctedParens.get(k);
            withMul.add(a);
            if (k + 1 < correctedParens.size()) {
                String b = correctedParens.get(k + 1);
                // tokenSymbolsのインデックスは元のtokensに対応しているため、調整が必要
                // 簡易的にはnullを渡す（位置情報は使わない）
                DetSymbol symA = null;
                DetSymbol symB = null;
                if (needImplicitMul(a, b, symA, symB)) {
                    withMul.add("*");
                }
            }
        }

        // 12) 文字列化
        String expr = String.join("", withMul);

        // 13) ちょいデバッグしやすく
        if (unbalancedParen(expr)) warnings.add("括弧の対応が取れていません（検出ミスの可能性があります）");

        return new Result(expr, warnings);
    }
    
    /**
     * 関数名のマージ（複数文字の関数名を1つのトークンにまとめる）
     * 例: 's','i','n' → "sin", 'l','o','g' → "log"
     */
    private List<DetSymbol> mergeFunctionNames(List<DetSymbol> symbols, List<String> warnings) {
        // 認識対象の関数名リスト
        String[] functionNames = {"sin", "cos", "tan", "sec", "csc", "cot", "ln", "log", "lim"};
        
        List<DetSymbol> result = new ArrayList<>();
        int i = 0;
        
        while (i < symbols.size()) {
            // 関数名の候補をチェック
            boolean matched = false;
            for (String funcName : functionNames) {
                if (i + funcName.length() <= symbols.size()) {
                    // 連続する文字列が関数名と一致するかチェック
                    StringBuilder candidate = new StringBuilder();
                    for (int j = 0; j < funcName.length(); j++) {
                        candidate.append(symbols.get(i + j).token);
                    }
                    
                    if (candidate.toString().equals(funcName)) {
                        // 関数名としてマージ
                        // 最初のシンボルのbboxを使用（簡易実装）
                        DetSymbol first = symbols.get(i);
                        DetSymbol merged = new DetSymbol(
                            first.cls,
                            funcName,
                            first.score, // 平均スコアを計算する方が良いが、簡易的に最初のスコアを使用
                            first.box
                        );
                        result.add(merged);
                        i += funcName.length();
                        matched = true;
                        break;
                    }
                }
            }
            
            if (!matched) {
                result.add(symbols.get(i));
                i++;
            }
        }
        
        return result;
    }
    
    /**
     * ルート記号の処理（√記号の直後の式を括弧で囲む）
     */
    private List<String> processSqrtSymbols(List<String> tokens, List<DetSymbol> tokenSymbols, List<String> warnings) {
        List<String> result = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals("√")) {
                result.add("sqrt");
                result.add("(");
                // 次のトークンから式の終わりまでを括弧で囲む
                // 簡易実装：次の数個のトークンを括弧で囲む（実際はより高度な解析が必要）
                int j = i + 1;
                int parenCount = 0;
                while (j < tokens.size()) {
                    String tok = tokens.get(j);
                    if (tok.equals("(")) parenCount++;
                    if (tok.equals(")")) {
                        if (parenCount == 0) break;
                        parenCount--;
                    }
                    // 演算子で終わる場合も終了（簡易実装）
                    if (parenCount == 0 && (tok.equals("+") || tok.equals("-") || tok.equals("*") || tok.equals("/"))) {
                        break;
                    }
                    j++;
                }
                // 式を追加
                for (int k = i + 1; k < j; k++) {
                    result.add(tokens.get(k));
                }
                result.add(")");
                i = j - 1; // ループでi++されるので-1
            } else {
                result.add(tokens.get(i));
            }
        }
        
        return result;
    }
    
    /**
     * 絶対値の処理（|...|をabs(...)に変換）
     */
    private List<String> processAbsoluteValue(List<String> tokens, List<String> warnings) {
        List<String> result = new ArrayList<>();
        List<Integer> absPositions = new ArrayList<>();
        
        // 絶対値記号|の位置を記録
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals("|")) {
                absPositions.add(i);
            }
        }
        
        // ペアを見つけてabs(...)に変換
        boolean[] used = new boolean[tokens.size()];
        for (int i = 0; i < absPositions.size(); i++) {
            int openIdx = absPositions.get(i);
            if (used[openIdx]) continue;
            
            // 対応する閉じ|を探す
            int closeIdx = -1;
            for (int j = i + 1; j < absPositions.size(); j++) {
                int candidateIdx = absPositions.get(j);
                if (!used[candidateIdx]) {
                    closeIdx = candidateIdx;
                    break;
                }
            }
            
            if (closeIdx > openIdx) {
                // abs(...)に変換
                used[openIdx] = true;
                used[closeIdx] = true;
                
                // トークンを変換
                for (int k = 0; k < tokens.size(); k++) {
                    if (k == openIdx) {
                        result.add("abs");
                        result.add("(");
                    } else if (k == closeIdx) {
                        result.add(")");
                    } else if (!used[k] || (k > openIdx && k < closeIdx)) {
                        result.add(tokens.get(k));
                    }
                }
            }
        }
        
        // ペアが見つからなかった場合はそのまま
        if (result.isEmpty()) {
            return tokens;
        }
        
        return result;
    }
    
    /**
     * 分数の処理（分数線の上下を検出して(numerator)/(denominator)に変換）
     * 簡易実装：水平線状の-記号で上下にシンボルがある場合を分数とみなす
     */
    private List<String> processFractions(List<String> tokens, List<DetSymbol> tokenSymbols, List<String> warnings) {
        // 簡易実装：分数線の検出は複雑なので、後で拡張
        // 現時点ではそのまま返す
        return tokens;
    }
    
    /**
     * 微分演算子の処理（d/dx構造を検出）
     */
    private List<String> processDerivatives(List<String> tokens, List<String> warnings) {
        List<String> result = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            // d/dx パターンを検出
            if (i + 3 < tokens.size() &&
                tokens.get(i).equals("d") &&
                tokens.get(i + 1).equals("/") &&
                tokens.get(i + 2).equals("d") &&
                isVariable(tokens.get(i + 3))) {
                
                result.add("diff");
                result.add("(");
                result.add(tokens.get(i + 3)); // 変数名
                result.add(")");
                i += 3; // ループでi++されるので+3
            } else {
                result.add(tokens.get(i));
            }
        }
        
        return result;
    }
    
    /**
     * 極限の処理（lim_{x→a}構造を検出）
     */
    private List<String> processLimits(List<String> tokens, List<String> warnings) {
        List<String> result = new ArrayList<>();
        
        for (int i = 0; i < tokens.size(); i++) {
            // lim パターンを検出（簡易実装）
            if (i < tokens.size() && tokens.get(i).equals("lim")) {
                result.add("limit");
                result.add("(");
                // 次のトークンから式を探す（簡易実装）
                int j = i + 1;
                while (j < tokens.size() && !tokens.get(j).equals("(")) {
                    j++;
                }
                if (j < tokens.size()) {
                    // 括弧内の式をコピー
                    int parenCount = 1;
                    j++;
                    while (j < tokens.size() && parenCount > 0) {
                        if (tokens.get(j).equals("(")) parenCount++;
                        if (tokens.get(j).equals(")")) parenCount--;
                        if (parenCount > 0) {
                            result.add(tokens.get(j));
                        }
                        j++;
                    }
                }
                result.add(")");
                i = j - 1;
            } else {
                result.add(tokens.get(i));
            }
        }
        
        return result;
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
        // 例: 2 x ) | は後ろに値が来たら掛け算が必要
        return isNumberLike(a) || isVariable(a) || a.equals(")") || a.equals("|");
    }

    private boolean isAtomStart(String b) {
        // 次が "値を開始する" トークンなら true
        // 例: x 2 ( sin sqrt √ は前が値なら掛け算が必要
        return isNumberLike(b) || isVariable(b) || b.equals("(") || b.equals("|") || isFunc(b) || b.equals("sqrt") || b.equals("√");
    }

    private boolean isFunc(String t) {
        return t.equals("sin") || t.equals("cos") || t.equals("tan") || t.equals("sec") || 
               t.equals("csc") || t.equals("cot") || t.equals("ln") || t.equals("log") || 
               t.equals("exp") || t.equals("sqrt") || t.equals("abs") || t.equals("lim") ||
               t.equals("limit") || t.equals("diff");
    }

    private boolean isOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/") || t.equals("^") || t.equals("=");
    }

    private boolean needImplicitMul(String a, String b, DetSymbol symA, DetSymbol symB) {
        // 明示演算子の前後は不要
        if (a.equals("+") || a.equals("-") || a.equals("*") || a.equals("/") || a.equals("^")) return false;
        if (b.equals("+") || b.equals("-") || b.equals("*") || b.equals("/") || b.equals("^")) return false;

        // 括弧の処理：条件付きで掛け算を入れる
        // ) の右側に数字や変数が来る場合: (-5)2 → (-5)*2
        if (a.equals(")") && (isNumberLike(b) || isVariable(b))) {
            return true;
        }
        // ( の左側に数字や変数がある場合: 2(x+1) → 2*(x+1)
        if (b.equals("(") && (isNumberLike(a) || isVariable(a))) {
            return true;
        }
        // それ以外の括弧の前後では掛け算を入れない
        if (a.equals("(") || b.equals("(")) return false;
        if (a.equals(")") || b.equals(")")) return false;

        // 例: "sin" "(" は掛け算じゃない（関数呼び出し）
        if (isFunc(a) && b.equals("(")) return false;
        if (a.equals("sqrt") && b.equals("(")) return false;

        // 数字同士の場合は掛け算を入れない（完全に禁止）
        if (isNumberLike(a) && isNumberLike(b)) {
            return false;
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
    
    private boolean isVariable(String token) {
        // 小文字アルファベット1文字を変数とみなす
        if (token == null || token.length() != 1) return false;
        char c = token.charAt(0);
        return c >= 'a' && c <= 'z';
    }
    
    /**
     * 括弧の対応を修正
     * (が連続して認識された場合、後半の(を)に修正する
     * )が連続して認識された場合、前半の)を(に修正する
     * ただし、ネストされた括弧の場合はそのまま
     */
    private List<String> fixParenMismatch(List<String> tokens, List<String> warnings) {
        List<String> result = new ArrayList<>(tokens);
        
        // 最大で10回まで修正を試みる（無限ループ防止）
        int maxIterations = 10;
        int iteration = 0;
        Set<Integer> fixedPositions = new HashSet<>(); // 修正済み位置を記録
        
        while (iteration < maxIterations) {
            // 括弧のバランスを計算
            int openCount = 0;
            int closeCount = 0;
            for (String token : result) {
                if (token.equals("(")) openCount++;
                if (token.equals(")")) closeCount++;
            }
            
            // バランスが取れていても、実際の対応関係をチェック
            boolean hasMismatch = false;
            int checkBalance = 0;
            for (int i = 0; i < result.size(); i++) {
                String token = result.get(i);
                if (token.equals("(")) {
                    checkBalance++;
                } else if (token.equals(")")) {
                    checkBalance--;
                    if (checkBalance < 0) {
                        // 対応する開き括弧がない閉じ括弧がある
                        hasMismatch = true;
                        break;
                    }
                }
            }
            // 最後のバランスもチェック
            if (checkBalance != 0) {
                hasMismatch = true;
            }
            
            // バランスが取れていて、対応関係も正しければ終了
            if (openCount == closeCount && !hasMismatch) {
                break;
            }
            
            boolean fixed = false;
            
            // バランスが取れていても、対応関係が正しくない場合の修正
            if (openCount == closeCount && hasMismatch) {
                // 左から右に走査して、対応する開き括弧がない閉じ括弧を開き括弧に修正
                int mismatchBalance = 0;
                for (int i = 0; i < result.size(); i++) {
                    // 既に修正済みの位置はスキップ
                    if (fixedPositions.contains(i)) {
                        String token = result.get(i);
                        if (token.equals("(")) mismatchBalance++;
                        else if (token.equals(")")) mismatchBalance--;
                        continue;
                    }
                    
                    String token = result.get(i);
                    if (token.equals("(")) {
                        mismatchBalance++;
                    } else if (token.equals(")")) {
                        if (mismatchBalance <= 0) {
                            // 対応する開き括弧がない閉じ括弧を開き括弧に修正
                            result.set(i, "(");
                            fixedPositions.add(i);
                            fixed = true;
                            warnings.add(String.format("括弧の対応を修正: 未対応の)を(に変更（位置%d、対応関係修正）", i));
                            break; // 1つずつ修正
                        } else {
                            mismatchBalance--;
                        }
                    }
                }
                
                // まだ問題がある場合、右から左に走査
                if (!fixed) {
                    mismatchBalance = 0;
                    for (int i = result.size() - 1; i >= 0; i--) {
                        // 既に修正済みの位置はスキップ
                        if (fixedPositions.contains(i)) {
                            String token = result.get(i);
                            if (token.equals(")")) mismatchBalance++;
                            else if (token.equals("(")) mismatchBalance--;
                            continue;
                        }
                        
                        String token = result.get(i);
                        if (token.equals(")")) {
                            mismatchBalance++;
                        } else if (token.equals("(")) {
                            if (mismatchBalance <= 0) {
                                // 対応する閉じ括弧がない開き括弧を閉じ括弧に修正
                                result.set(i, ")");
                                fixedPositions.add(i);
                                fixed = true;
                                warnings.add(String.format("括弧の対応を修正: 未対応の(を)に変更（位置%d、対応関係修正）", i));
                                break; // 1つずつ修正
                            } else {
                                mismatchBalance--;
                            }
                        }
                    }
                }
            }
            // 開き括弧が余っている場合の修正
            else if (openCount > closeCount) {
                int excess = openCount - closeCount;
                int fixedCount = 0;
                
                // 優先度1: 左から右に走査して、連続する((の後半を)に修正（最も確実）
                int parenBalance = 0;
                for (int i = 0; i < result.size() && fixedCount < excess; i++) {
                    // 既に修正済みの位置はスキップ
                    if (fixedPositions.contains(i)) {
                        String token = result.get(i);
                        if (token.equals("(")) parenBalance++;
                        else if (token.equals(")")) parenBalance--;
                        continue;
                    }
                    
                    String token = result.get(i);
                    
                    if (token.equals("(")) {
                        parenBalance++;
                        
                        // 次のトークンも(で、かつ括弧のバランスが1以上（開き括弧が余っている）場合
                        if (i + 1 < result.size() && result.get(i + 1).equals("(") && !fixedPositions.contains(i + 1) && parenBalance > 0) {
                            // 後半の(を)に修正
                            result.set(i + 1, ")");
                            fixedPositions.add(i + 1);
                            fixedCount++;
                            parenBalance--;  // 修正したのでバランスを調整
                            warnings.add(String.format("括弧の対応を修正: 連続する((の後半を)に変更（位置%d）", i + 1));
                            i++; // 修正したトークンをスキップ
                        }
                    } else if (token.equals(")")) {
                        parenBalance--;
                        if (parenBalance < 0) {
                            parenBalance = 0;
                        }
                    }
                }
                
                // 優先度2: まだ余っている場合、右から左に走査して修正
                if (fixedCount < excess) {
                    parenBalance = 0;
                    for (int i = result.size() - 1; i >= 0 && fixedCount < excess; i--) {
                        // 既に修正済みの位置はスキップ
                        if (fixedPositions.contains(i)) {
                            String token = result.get(i);
                            if (token.equals(")")) parenBalance++;
                            else if (token.equals("(")) parenBalance--;
                            continue;
                        }
                        
                        String token = result.get(i);
                        
                        if (token.equals(")")) {
                            parenBalance++;
                        } else if (token.equals("(")) {
                            if (parenBalance == 0 && fixedCount < excess) {
                                // 対応する閉じ括弧がない開き括弧を閉じ括弧に修正
                                result.set(i, ")");
                                fixedPositions.add(i);
                                fixedCount++;
                                warnings.add(String.format("括弧の対応を修正: 未対応の(を)に変更（位置%d、右から）", i));
                            } else {
                                parenBalance--;
                            }
                        }
                    }
                }
                if (fixedCount > 0) {
                    fixed = true;
                }
            }
            // 閉じ括弧が余っている場合の修正
            else if (closeCount > openCount) {
                int excess = closeCount - openCount;
                int fixedCount = 0;
                
                // 優先度1: 左から右に走査して、連続する))の前半を(に修正（最も確実）
                int parenBalance = 0;
                for (int i = 0; i < result.size() && fixedCount < excess; i++) {
                    // 既に修正済みの位置はスキップ
                    if (fixedPositions.contains(i)) {
                        String token = result.get(i);
                        if (token.equals("(")) parenBalance++;
                        else if (token.equals(")")) parenBalance--;
                        continue;
                    }
                    
                    String token = result.get(i);
                    
                    if (token.equals("(")) {
                        parenBalance++;
                    } else if (token.equals(")")) {
                        // 対応する開き括弧がない閉じ括弧をチェック
                        if (parenBalance <= 0) {
                            // 次のトークンも)で、かつ括弧のバランスが0以下（閉じ括弧が余っている）場合
                            if (i + 1 < result.size() && result.get(i + 1).equals(")") && fixedCount < excess) {
                                // 前半の)を(に修正
                                result.set(i, "(");
                                fixedPositions.add(i);
                                fixedCount++;
                                parenBalance++;  // 修正したのでバランスを調整
                                warnings.add(String.format("括弧の対応を修正: 連続する))の前半を(に変更（位置%d）", i));
                            } else if (fixedCount < excess) {
                                // 単独の未対応の)を(に修正
                                result.set(i, "(");
                                fixedPositions.add(i);
                                fixedCount++;
                                parenBalance++;
                                warnings.add(String.format("括弧の対応を修正: 未対応の)を(に変更（位置%d）", i));
                            }
                        } else {
                            parenBalance--;
                        }
                    }
                }
                
                // 優先度2: まだ余っている場合、右から左に走査して修正
                if (fixedCount < excess) {
                    // 右から左に走査して、対応する開き括弧がない閉じ括弧を開き括弧に修正
                    // 括弧のバランスを右から左に計算（逆方向）
                    parenBalance = 0;
                    for (int i = result.size() - 1; i >= 0 && fixedCount < excess; i--) {
                        String token = result.get(i);
                        
                        // 右から見ると、開き括弧は閉じ括弧として、閉じ括弧は開き括弧として扱う
                        if (token.equals("(")) {
                            // 右から見ると、開き括弧は閉じ括弧として扱う
                            parenBalance++;
                        } else if (token.equals(")")) {
                            // 右から見ると、閉じ括弧は開き括弧として扱う
                            if (parenBalance == 0 && fixedCount < excess) {
                                // 対応する開き括弧がない閉じ括弧を開き括弧に修正
                                result.set(i, "(");
                                fixedCount++;
                                warnings.add(String.format("括弧の対応を修正: 未対応の)を(に変更（位置%d、右から）", i));
                            } else if (parenBalance > 0) {
                                parenBalance--;
                            }
                        }
                    }
                }
                fixed = true;
            }
            
            // 修正が行われなかった場合は終了
            if (!fixed) {
                break;
            }
            
            iteration++;
        }
        
        return result;
    }
}
