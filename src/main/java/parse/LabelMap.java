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
        map.put("exp", "exp");
        map.put("log", "log");

        // NOTE:
        // 分数バーが専用クラスとして出るなら：
        // map.put("frac_bar", "FRAC_BAR");
        // みたいに内部トークンとして扱える
        
        // クラスID → クラス名のマッピング（YOLOv5の18クラス）
        // 順序: 数字0-9, 演算子+,-,*,/,=, 変数x, 括弧(,)
        // 実際のモデルは18クラス（出力次元23 = 5 + 18）
        idToClass = new String[]{
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",  // 0-9: 数字（10クラス）
            "+", "-", "*", "/", "=",                            // 10-14: 演算子（5クラス）
            "x",                                                 // 15: 変数（1クラス）
            "(", ")"                                             // 16-17: 括弧（2クラス）
        };
        // 合計18クラス（0-17）
    }

    /**
     * クラスIDからクラス名を取得
     * 
     * @param classId クラスID（0-17）
     * @return クラス名（例: "0", "+", "x"）
     */
    public String getClassLabel(int classId) {
        if (classId < 0 || classId >= idToClass.length) {
            throw new IllegalArgumentException("Invalid class ID: " + classId);
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
        if (s.equals("+") || s.equals("-") || s.equals("*") || s.equals("/") || s.equals("(") || s.equals(")")) return true;
        if (s.equals("x")) return true;
        if (s.equals("sqrt") || s.equals("sin") || s.equals("cos") || s.equals("exp") || s.equals("log")) return true;
        // 数字（整数）
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }
}
