package io;

import ast.Expr;
import org.json.JSONObject;

/**
 * AST → JSON → AST の往復変換をテストするデモ
 * JSON変換機能が正しく動作することを確認する
 */
public class DemoJsonRoundTrip {
    public static void main(String[] args) throws Exception {
        // 既存のJSONファイルを読み込む
        String jsonPath = (args.length > 0) ? args[0] : "samples/expr/add_mul.json";
        String jsonText = java.nio.file.Files.readString(java.nio.file.Path.of(jsonPath));
        
        System.out.println("[1] JSONファイルを読み込み:");
        System.out.println(jsonText);
        System.out.println();

        // JSON → AST
        Expr expr1 = AstJson.parseRoot(jsonText);
        System.out.println("[2] ASTに変換して評価 (x=1.0):");
        System.out.println("  result = " + expr1.eval(1.0));
        System.out.println();

        // AST → JSON
        JSONObject jsonObj = AstJson.toJsonV1(expr1);
        String jsonOutput = jsonObj.toString(2);
        System.out.println("[3] ASTをJSONに変換:");
        System.out.println(jsonOutput);
        System.out.println();

        // JSON → AST (往復確認)
        Expr expr2 = AstJson.parseRoot(jsonOutput);
        System.out.println("[4] 再度JSONからASTに変換して評価 (x=1.0):");
        System.out.println("  result = " + expr2.eval(1.0));
        System.out.println();

        // 結果が一致するか確認
        double v1 = expr1.eval(2.0);
        double v2 = expr2.eval(2.0);
        if (Math.abs(v1 - v2) < 1e-9) {
            System.out.println("[OK] 往復変換が正しく動作しています！");
        } else {
            System.out.println("[NG] 往復変換で値が一致しません: " + v1 + " vs " + v2);
        }
    }
}
