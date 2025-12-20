package parse;

import java.util.*;

public class LabelMap {
    private final Map<String, String> map = new HashMap<>();

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
