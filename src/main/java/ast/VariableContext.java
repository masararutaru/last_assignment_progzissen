package ast;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 変数の値を保持するコンテキスト
 */
public class VariableContext {
    private final Map<String, Double> variables;
    
    public VariableContext() {
        this.variables = new HashMap<>();
    }
    
    /**
     * 変数の値を設定
     */
    public void setVariable(String name, double value) {
        variables.put(name, value);
    }
    
    /**
     * 変数の値を取得（存在しない場合はデフォルト値1.0を返す）
     */
    public double getVariable(String name) {
        return variables.getOrDefault(name, 1.0);
    }
    
    /**
     * 全ての変数名を取得
     */
    public Set<String> getVariableNames() {
        return variables.keySet();
    }
    
    /**
     * 変数が設定されているかチェック
     */
    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }
    
    /**
     * 全ての変数をクリア
     */
    public void clear() {
        variables.clear();
    }
}

