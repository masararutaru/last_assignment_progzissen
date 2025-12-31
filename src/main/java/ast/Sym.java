package ast;

public class Sym implements Expr {
    public final String name;
    private static VariableContext defaultContext = new VariableContext();
    
    public Sym(String name) { 
        this.name = name; 
    }
    
    /**
     * グローバルな変数コンテキストを設定
     */
    public static void setGlobalContext(VariableContext context) {
        defaultContext = context;
    }
    
    @Override 
    public double eval(double x) {
        // 変数コンテキストから値を取得
        if (name != null && name.length() == 1 && name.charAt(0) >= 'a' && name.charAt(0) <= 'z') {
            return defaultContext.getVariable(name);
        }
        // θなどの特殊変数も対応
        if (name != null && (name.equals("θ") || name.equals("theta"))) {
            return defaultContext.getVariable("θ");
        }
        throw new IllegalArgumentException("Unknown symbol: " + name);
    }
}
