package io;

import parse.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * ONNX推論のデモクラス
 * 
 * 使い方:
 *   java io.DemoOnnxInference assets/model.onnx path/to/image.png
 */
public class DemoOnnxInference {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java io.DemoOnnxInference <model.onnx> <image.png>");
            System.exit(1);
        }
        
        String modelPath = args[0];
        String imagePath = args[1];
        
        try {
            // 画像を読み込む
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                System.err.println("Image not found: " + imagePath);
                System.exit(1);
            }
            
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                System.err.println("Failed to load image: " + imagePath);
                System.exit(1);
            }
            
            int imageW = image.getWidth();
            int imageH = image.getHeight();
            System.out.println("[INFO] Loaded image: " + imageW + "x" + imageH);
            
            // ONNX推論を実行
            try (OnnxInference inference = new OnnxInference(modelPath)) {
                System.out.println("[INFO] Running inference...");
                Detection detection = inference.detect(image, imageW, imageH);
                
                // 結果を表示
                System.out.println("[INFO] Detected " + detection.symbols.size() + " symbols:");
                for (DetSymbol sym : detection.symbols) {
                    System.out.printf("  %s (score=%.3f, bbox=[%.1f,%.1f,%.1f,%.1f])\n",
                        sym.token, sym.score,
                        sym.box.x1, sym.box.y1, sym.box.x2, sym.box.y2);
                }
                
                // SpatialToExprで式文字列に変換
                SpatialToExpr spatialToExpr = new SpatialToExpr();
                SpatialToExpr.Result result = spatialToExpr.buildExprString(detection);
                
                if (!result.warnings.isEmpty()) {
                    System.out.println("[WARN] Warnings:");
                    for (String warn : result.warnings) {
                        System.out.println("  " + warn);
                    }
                }
                
                System.out.println("[EXPR] " + result.expr);
                
                // 式をパースして評価（オプション）
                if (!result.expr.isEmpty()) {
                    try {
                        ast.Expr expr = Parser.parse(result.expr);
                        double x = 1.0;
                        double value = expr.eval(x);
                        System.out.println("[EVAL] x=" + x + " -> " + value);
                    } catch (Exception e) {
                        System.out.println("[EVAL] Failed to evaluate: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

