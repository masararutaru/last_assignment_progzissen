package parse;

import ast.Expr;
import io.AstJson;
import java.util.*;

public class DemoDetectionsToAst {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: DemoDetectionsToAst <detections.json>");
            System.exit(1);
        }

        String path = args[0];

        LabelMap labelMap = new LabelMap();
        List<String> loadWarnings = new ArrayList<>();
        Detection det = DetectionJson.load(path, labelMap, loadWarnings);
        
        SpatialToExpr conv = new SpatialToExpr();
        SpatialToExpr.Result r = conv.buildExprString(det);

        // 警告を先に表示（読み込み時の警告 + 変換時の警告）
        for (String w : loadWarnings) {
            System.out.println("[warn] " + w);
        }
        for (String w : r.warnings) {
            System.out.println("[warn] " + w);
        }

        // 式を表示
        System.out.println("[expr] " + r.expr);

        // 既存の文字列パーサを使用して式文字列をASTに変換
        Expr e = Parser.parse(r.expr);

        double x = 1.0;
        double y = e.eval(x);
        System.out.println("[eval] x=" + x + " => " + y);

        // ASTをJSON形式で出力（GUIでの表示やデバッグ用）
        System.out.println("[ast-json]");
        System.out.println(AstJson.toJsonV1(e).toString(2));
    }
}
