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
        
        // クラスID → クラス名のマッピング（訓練時使用クラス：18クラス）
        // 実際のモデル訓練で使用されたクラス順序に合わせる必要がある
        // Kaggle Aida Calculus Math Handwriting Datasetで訓練されたモデルは18クラス（0-17）
        // 順序: 数字0-9, 演算子+,-,*,/,=, 変数x, 括弧(,)
        // OnnxInference.javaの括弧誤認識補正コード（bestClass == 16 && secondBestClass == 17）と一致
        idToClass = new String[]{
            // 数字（10クラス: 0-9）
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            
            // 基本演算子（5クラス: 10-14）
            "+", "-", "*", "/", "=",
            
            // 変数（1クラス: 15）
            "x",
            
            // 括弧（2クラス: 16-17）
            "(", ")"
        };
        // 合計18クラス（0-17）
    }

    /**
     * クラスIDからクラス名を取得
     * 
     * @param classId クラスID（0-17）
     * @return クラス名（例: "0", "+", "x", "(", ")"）
     */
    public String getClassLabel(int classId) {
        if (classId < 0 || classId >= idToClass.length) {
            throw new IllegalArgumentException("Invalid class ID: " + classId + " (valid range: 0-" + (idToClass.length - 1) + ")");
        }
        return idToClass[classId];
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
