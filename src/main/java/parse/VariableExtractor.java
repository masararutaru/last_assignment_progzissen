package parse;

import ast.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 式に含まれる変数を抽出するユーティリティ
 */
public class VariableExtractor {
    /**
     * 式に含まれる変数名を抽出
     */
    public static Set<String> extractVariables(Expr expr) {
        Set<String> variables = new HashSet<>();
        extractVariablesRecursive(expr, variables);
        return variables;
    }
    
    private static void extractVariablesRecursive(Expr expr, Set<String> variables) {
        if (expr instanceof Sym) {
            Sym sym = (Sym) expr;
            String name = sym.name;
            // a-zの変数とθを抽出
            if (name != null && name.length() == 1 && name.charAt(0) >= 'a' && name.charAt(0) <= 'z') {
                variables.add(name);
            } else if (name != null && (name.equals("θ") || name.equals("theta"))) {
                variables.add("θ");
            }
        } else if (expr instanceof Add) {
            Add add = (Add) expr;
            for (Expr arg : add.args) {
                extractVariablesRecursive(arg, variables);
            }
        } else if (expr instanceof Mul) {
            Mul mul = (Mul) expr;
            for (Expr arg : mul.args) {
                extractVariablesRecursive(arg, variables);
            }
        } else if (expr instanceof Div) {
            Div div = (Div) expr;
            extractVariablesRecursive(div.getLeft(), variables);
            extractVariablesRecursive(div.getRight(), variables);
        } else if (expr instanceof Pow) {
            Pow pow = (Pow) expr;
            extractVariablesRecursive(pow.getBase(), variables);
            extractVariablesRecursive(pow.getExp(), variables);
        } else if (expr instanceof Func) {
            Func func = (Func) expr;
            for (Expr arg : func.getArgs()) {
                extractVariablesRecursive(arg, variables);
            }
        }
        // Numは変数ではないのでスキップ
    }
}

