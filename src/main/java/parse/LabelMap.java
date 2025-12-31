package parse;

import java.util.*;

public class LabelMap {
    private final Map<String, String> map = new HashMap<>();
    private final String[] idToClass;  // クラスID → クラス名のマッピング

    public LabelMap() {
        // 例：モデル側のクラス名に合わせて調整していく
        // すでに cls が "+", "1", "sin" みたいに出るなら、そのままでもOK
        for (int d = 0; d <= 9; d++) {
            map.put("digit_" + d, String.valueOf(d));
        }
        map.put("plus", "+");
        map.put("minus", "-");
        map.put("times", "*");
        map.put("divide", "/");
        map.put("lparen", "(");
        map.put("rparen", ")");
        map.put("var_x", "x");

        map.put("sqrt", "sqrt");
        map.put("sin", "sin");
        map.put("cos", "cos");
        map.put("tan", "tan");
        map.put("sec", "sec");
        map.put("csc", "csc");
        map.put("cot", "cot");
        map.put("ln", "ln");
        map.put("exp", "exp");
        map.put("log", "log");
        map.put("lim", "lim");
        map.put("abs", "abs");

        // NOTE:
        // 分数バーが専用クラスとして出るなら：
        // map.put("frac_bar", "FRAC_BAR");
        // みたいに内部トークンとして扱える
        
        // クラスID → クラス名のマッピング（拡張版：61クラス）
        // Python側のclasses.pyと順序を一致させる必要がある
        // 順序: 数字0-9, 小数点, 演算子+,-,*,/,=, アルファベットa-z, 括弧(,),|, ギリシャ文字・定数π,θ,∞, 特殊記号→,√, 関数名
        idToClass = new String[]{
            // 数字（10クラス: 0-9）
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            
            // 小数点（1クラス: 10）
            ".",
            
            // 基本演算子（5クラス: 11-15）
            "+", "-", "*", "/", "=",
            
            // アルファベット変数（26クラス: 16-41）
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            
            // 括弧類（3クラス: 42-44）
            "(", ")", "|",
            
            // ギリシャ文字・定数（3クラス: 45-47）
            "π", "θ", "∞",
            // 注意: "e"（自然対数の底）はアルファベット変数の"e"と重複するため削除
            
            // 特殊記号（2クラス: 48-49）
            "→", "√",
            
            // 関数名（12クラス: 50-61）
            "sin", "cos", "tan", "sec", "csc", "cot", "ln", "log", "exp", "sqrt", "lim", "abs"
        };
        // 合計61クラス（0-60）
    }

    /**
     * クラスIDからクラス名を取得
     * 
     * @param classId クラスID（0-60）
     * @return クラス名（例: "0", "+", "x", "π", "→", "sin", "lim"）
     * @throws IllegalArgumentException クラスIDが範囲外の場合
     */
    public String getClassLabel(int classId) {
        if (classId < 0 || classId >= idToClass.length) {
            throw new IllegalArgumentException("Invalid class ID: " + classId + " (valid range: 0-" + (idToClass.length - 1) + ")");
        }
        return idToClass[classId];
    }
    
    /**
     * クラスIDが有効かどうかをチェック
     * 
     * @param classId クラスID
     * @return 有効な場合はtrue
     */
    public boolean isValidClassId(int classId) {
        return classId >= 0 && classId < idToClass.length;
    }

    /**
     * クラス名からトークンに変換
     * 
     * @param cls クラス名（例: "digit_3", "+", "sin"）
     * @return トークン（例: "3", "+", "sin"）
     */
    public String getToken(String cls) {
        return toToken(cls);
    }

    public String toToken(String cls) {
        if (map.containsKey(cls)) return map.get(cls);

        // cls がすでに "+", "7", "sin" などなら通す（便利）
        if (isAlreadyTokenLike(cls)) return cls;

        throw new IllegalArgumentException("Unknown class label: " + cls);
    }

    private boolean isAlreadyTokenLike(String s) {
        if (s == null || s.isEmpty()) return false;
        // 演算子・括弧
        if (s.equals("+") || s.equals("-") || s.equals("*") || s.equals("/") || s.equals("=")) return true;
        if (s.equals("(") || s.equals(")") || s.equals("|")) return true;
        // アルファベット変数（小文字a-z）
        if (s.length() == 1 && s.charAt(0) >= 'a' && s.charAt(0) <= 'z') return true;
        // 関数名
        if (s.equals("sqrt") || s.equals("sin") || s.equals("cos") || s.equals("tan") || 
            s.equals("sec") || s.equals("csc") || s.equals("cot") || s.equals("ln") || 
            s.equals("exp") || s.equals("log") || s.equals("lim") || s.equals("abs")) return true;
        // 特殊記号
        if (s.equals("π") || s.equals("θ") || s.equals("∞") || s.equals("→") || s.equals("√")) return true;
        // 数字（整数）
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }
}
