package io;

import ai.onnxruntime.*;
import parse.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * ONNX Runtimeを使用したYOLOv5推論クラス
 * 
 * 画像を入力として、検出結果（bbox + クラス + スコア）を返す。
 * NMSとスコアフィルタリングは後処理として実装。
 */
public class OnnxInference implements AutoCloseable {
    
    private final OrtEnvironment env;
    private final OrtSession session;
    private final int inputSize;  // 640 (YOLOv5標準)
    private int numClasses; // モデルの出力次元から動的に決定
    
    // 後処理パラメータ
    private double confidenceThreshold = 0.2;  // 信頼度閾値（重複検出を減らすため0.2に設定）
    private double nmsIouThreshold = 0.45;  // 異なるクラス間のIoU閾値
    private double nmsIouThresholdSameClass = 0.3;  // 同じクラス間のIoU閾値（より厳しく）
    
    /**
     * ONNXモデルを読み込む
     * 
     * @param modelPath ONNXモデルファイルのパス（例: "assets/model.onnx"）
     * @throws OrtException ONNX Runtimeのエラー
     */
    public OnnxInference(String modelPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        opts.setIntraOpNumThreads(4);  // CPU推論用
        
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new IllegalArgumentException("ONNX model not found: " + modelPath);
        }
        
        this.session = env.createSession(modelPath, opts);
        this.inputSize = 640;  // YOLOv5標準
        
        // モデル情報を表示してからクラス数を決定
        printModelInfo();
        
        // モデルの出力次元からクラス数を動的に取得
        try {
            NodeInfo outputInfo = session.getOutputInfo().values().iterator().next();
            TensorInfo tensorInfo = (TensorInfo) outputInfo.getInfo();
            long[] outputShape = tensorInfo.getShape();
            
            // 出力形状: [1, num_detections, 5+num_classes]
            // 最後の次元からクラス数を計算: 5(bbox+objectness) + num_classes
            if (outputShape.length >= 3) {
                int outputDim = (int) outputShape[outputShape.length - 1];
                this.numClasses = outputDim - 5;  // 5 = 4(bbox) + 1(objectness)
                System.out.println("[ONNX] 検出されたクラス数: " + this.numClasses + " (出力次元: " + outputDim + ")");
            } else {
                // フォールバック: デフォルトで61クラス（拡張版）
                this.numClasses = 61;
                System.out.println("[ONNX] 警告: 出力形状からクラス数を取得できませんでした。デフォルト値61（拡張版）を使用します。");
            }
        } catch (Exception e) {
            // エラー時はデフォルトで61クラス（拡張版）
            this.numClasses = 61;
            System.err.println("[ONNX] 警告: モデル情報の取得に失敗しました。デフォルト値61（拡張版）を使用します: " + e.getMessage());
        }
    }
    
    /**
     * モデルの入力・出力情報を表示（デバッグ用）
     */
    private void printModelInfo() {
        try {
            System.out.println("[ONNX] Model loaded successfully");
            System.out.println("  Inputs:");
            for (NodeInfo input : session.getInputInfo().values()) {
                System.out.println("    " + input.getName() + ": " + input.getInfo().toString());
            }
            System.out.println("  Outputs:");
            for (NodeInfo output : session.getOutputInfo().values()) {
                System.out.println("    " + output.getName() + ": " + output.getInfo().toString());
            }
        } catch (Exception e) {
            System.err.println("[WARN] Failed to print model info: " + e.getMessage());
        }
    }
    
    /**
     * リサイズ情報を保持する内部クラス
     */
    private static class ResizeInfo {
        double scale;
        int offsetX;
        int offsetY;
        int srcW;
        int srcH;
        
        ResizeInfo(double scale, int offsetX, int offsetY, int srcW, int srcH) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.srcW = srcW;
            this.srcH = srcH;
        }
    }
    
    /**
     * 前処理結果を保持する内部クラス
     */
    private static class PreprocessResult {
        final float[][][] normalized;
        final ResizeInfo resizeInfo;
        
        PreprocessResult(float[][][] normalized, ResizeInfo resizeInfo) {
            this.normalized = normalized;
            this.resizeInfo = resizeInfo;
        }
    }
    
    /**
     * 画像から検出結果を取得
     * 
     * @param image 入力画像（任意サイズ）
     * @param imageW 画像の幅（元のサイズ）
     * @param imageH 画像の高さ（元のサイズ）
     * @return 検出結果（Detectionオブジェクト）
     * @throws OrtException ONNX Runtimeのエラー
     */
    public Detection detect(BufferedImage image, int imageW, int imageH) throws OrtException {
        // 1. 前処理：画像を640x640にリサイズして正規化（リサイズ情報も取得）
        PreprocessResult preprocessResult = preprocessImage(image);
        
        // 2. ONNX推論実行
        float[][][] rawOutput = runInference(preprocessResult.normalized);
        
        // 3. 後処理：NMS + スコアフィルタリング（リサイズ情報を使用して座標変換）
        List<DetSymbol> detections = postProcess(rawOutput, imageW, imageH, preprocessResult.resizeInfo);
        
        return new Detection(imageW, imageH, detections);
    }
    
    /**
     * 画像の前処理（リサイズ + 正規化）
     * 
     * @param image 入力画像
     * @return 前処理結果（正規化済み画像データとリサイズ情報）
     */
    private PreprocessResult preprocessImage(BufferedImage image) {
        // 640x640にリサイズ（アスペクト比を保持してパディング）
        ResizeResult resizeResult = resizeWithPadding(image, inputSize, inputSize);
        BufferedImage resized = resizeResult.resizedImage;
        ResizeInfo resizeInfo = new ResizeInfo(
            resizeResult.scale,
            resizeResult.offsetX,
            resizeResult.offsetY,
            resizeResult.srcW,
            resizeResult.srcH
        );
        
        // RGBに変換して正規化（0-255 → 0.0-1.0）
        float[][][] normalized = new float[3][inputSize][inputSize];
        
        for (int y = 0; y < inputSize; y++) {
            for (int x = 0; x < inputSize; x++) {
                int rgb = resized.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // 0-255 → 0.0-1.0 に正規化
                normalized[0][y][x] = r / 255.0f;  // R
                normalized[1][y][x] = g / 255.0f;  // G
                normalized[2][y][x] = b / 255.0f;  // B
            }
        }
        
        return new PreprocessResult(normalized, resizeInfo);
    }
    
    /**
     * リサイズ情報と画像を保持する内部クラス
     */
    private static class ResizeResult {
        final BufferedImage resizedImage;
        final double scale;
        final int offsetX;
        final int offsetY;
        final int srcW;
        final int srcH;
        
        ResizeResult(BufferedImage img, double scale, int offsetX, int offsetY, int srcW, int srcH) {
            this.resizedImage = img;
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.srcW = srcW;
            this.srcH = srcH;
        }
    }
    
    /**
     * アスペクト比を保持してリサイズ（パディング付き）
     * 
     * @param image 元画像
     * @param targetW 目標幅
     * @param targetH 目標高さ
     * @return リサイズ情報とリサイズ済み画像
     */
    private ResizeResult resizeWithPadding(BufferedImage image, int targetW, int targetH) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        
        // スケール比を計算（小さい方に合わせる）
        double scale = Math.min((double) targetW / srcW, (double) targetH / srcH);
        int newW = (int) (srcW * scale);
        int newH = (int) (srcH * scale);
        
        // リサイズ
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
                          java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();
        
        // 白背景でパディング
        BufferedImage padded = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = padded.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, targetW, targetH);
        int offsetX = (targetW - newW) / 2;
        int offsetY = (targetH - newH) / 2;
        g2.drawImage(resized, offsetX, offsetY, null);
        g2.dispose();
        
        return new ResizeResult(padded, scale, offsetX, offsetY, srcW, srcH);
    }
    
    /**
     * ONNX推論を実行
     * 
     * @param preprocessed 前処理済み画像データ [3][640][640]
     * @return 生の推論結果 [1][num_detections][5+num_classes]
     * @throws OrtException ONNX Runtimeのエラー
     */
    private float[][][] runInference(float[][][] preprocessed) throws OrtException {
        // ONNX Runtimeの入力形式に変換: [1, 3, 640, 640]
        long[] shape = {1, 3, inputSize, inputSize};
        float[] flatData = new float[3 * inputSize * inputSize];
        
        int idx = 0;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < inputSize; y++) {
                for (int x = 0; x < inputSize; x++) {
                    flatData[idx++] = preprocessed[c][y][x];
                }
            }
        }
        
        // ONNX Tensor作成
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatData), shape);
        
        OrtSession.Result outputs = null;
        try {
            // 推論実行
            String inputName = session.getInputNames().iterator().next();
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, tensor);
            
            outputs = session.run(inputs);
            
            // 出力を取得（YOLOv5の出力は通常 [1, num_detections, 5+num_classes]）
            OnnxValue outputValue = outputs.get(0);
            float[][][] result = (float[][][]) outputValue.getValue();
            
            // デバッグ情報
            System.out.println("[DEBUG] 推論結果:");
            System.out.println("  出力形状: [" + result.length + "][" + 
                              (result.length > 0 ? result[0].length : 0) + "][" + 
                              (result.length > 0 && result[0].length > 0 ? result[0][0].length : 0) + "]");
            
            return result;
            
        } finally {
            tensor.close();
            if (outputs != null) {
                outputs.close();
            }
        }
    }
    
    /**
     * 後処理：NMS + スコアフィルタリング
     * 
     * @param rawOutput 生の推論結果 [batch][num_detections][5+num_classes]
     * @param imageW 元画像の幅
     * @param imageH 元画像の高さ
     * @param resizeInfo リサイズ情報
     * @return 検出シンボルのリスト
     */
    private List<DetSymbol> postProcess(float[][][] rawOutput, int imageW, int imageH, ResizeInfo resizeInfo) {
        // rawOutput: [1][num_detections][5+num_classes]
        // 5 = 4 (bbox: cx, cy, w, h) + 1 (objectness)
        float[][] detections = rawOutput[0];  // [num_detections][5+num_classes]
        
        // デバッグ情報
        System.out.println("[DEBUG] postProcess開始");
        System.out.println("  rawOutput形状: [" + rawOutput.length + "][" + 
                          (rawOutput.length > 0 ? rawOutput[0].length : 0) + "][" + 
                          (rawOutput.length > 0 && rawOutput[0].length > 0 ? rawOutput[0][0].length : 0) + "]");
        System.out.println("  検出候補数: " + detections.length);
        if (detections.length > 0) {
            System.out.println("  各候補の次元: " + detections[0].length);
        }
        System.out.println("  元画像サイズ: " + imageW + "x" + imageH);
        System.out.println("  リサイズ情報: scale=" + resizeInfo.scale + ", offsetX=" + resizeInfo.offsetX + ", offsetY=" + resizeInfo.offsetY);
        
        List<DetectionCandidate> candidates = new ArrayList<>();
        
        // 各検出候補をパース
        int candidateCount = 0;
        int filteredByScore = 0;
        int filteredByBbox = 0;
        
        for (int i = 0; i < detections.length; i++) {
            float[] det = detections[i];
            
            // bbox: [cx, cy, w, h] (640x640座標系での正規化座標 0.0-1.0)
            double cx = det[0];
            double cy = det[1];
            double w = det[2];
            double h = det[3];
            double objectness = det[4];
            
            // デバッグ: 最初の5個の候補の情報を表示
            if (i < 5) {
                System.out.println(String.format("  [候補%d] cx=%.3f cy=%.3f w=%.3f h=%.3f objectness=%.3f (形式判定: %s)", 
                    i, cx, cy, w, h, objectness, 
                    (cx > 1.0 || cy > 1.0 || w > 1.0 || h > 1.0) ? "ピクセル座標" : "正規化座標"));
            }
            
            // クラス確率を取得
            // 実際の出力次元を確認（配列の長さから）
            int actualOutputDim = det.length;
            int actualNumClasses = actualOutputDim - 5;  // 5 = 4(bbox) + 1(objectness)
            int classesToCheck = Math.min(numClasses, actualNumClasses);  // 安全のため小さい方を使用
            
            double maxScore = 0.0;
            int bestClass = -1;
            double secondBestScore = 0.0;
            int secondBestClass = -1;
            
            for (int c = 0; c < classesToCheck; c++) {
                int idx = 5 + c;
                if (idx >= det.length) {
                    break;  // 配列の範囲外を防ぐ
                }
                double classScore = det[idx];
                
                // スコア計算: objectness * classScore
                double baseScore = objectness * classScore;
                
                // 上位2つのクラスを記録（)と(の誤認識を防ぐため）
                if (baseScore > maxScore) {
                    secondBestScore = maxScore;
                    secondBestClass = bestClass;
                    maxScore = baseScore;
                    bestClass = c;
                } else if (baseScore > secondBestScore) {
                    secondBestScore = baseScore;
                    secondBestClass = c;
                }
            }
            
            // )が(に誤認識されるのを防ぐ: )のスコアが(のスコアに近い場合、)を優先
            // 拡張版では括弧はクラス42（(）と43（)）
            if (bestClass == 42 && secondBestClass == 43) {  // (が最良、)が2位
                double ratio = maxScore / (secondBestScore + 0.0001);  // 0除算防止
                if (ratio < 1.3) {  // スコア差が30%未満なら)を優先
                    // )を優先
                    int temp = bestClass;
                    bestClass = secondBestClass;
                    secondBestClass = temp;
                    double tempScore = maxScore;
                    maxScore = secondBestScore;
                    secondBestScore = tempScore;
                    if (i < 5) {
                        System.out.println(String.format("    [補正] )を優先 (スコア比=%.2f)", ratio));
                    }
                }
            }
            
            // デバッグ: 最初の5個の候補のスコア情報を表示
            if (i < 5) {
                System.out.println(String.format("    bestClass=%d maxScore=%.3f (2位: class=%d score=%.3f, 閾値=%.3f)", 
                    bestClass, maxScore, secondBestClass, secondBestScore, confidenceThreshold));
            }
            
            // スコア閾値でフィルタリング
            double classThreshold = confidenceThreshold;
            if (maxScore >= classThreshold && bestClass >= 0) {
                candidateCount++;
                // bbox: [cx, cy, w, h] 
                // モデルの出力形式を確認: cx=6.050などは既にピクセル座標（640x640）として返されている可能性がある
                // 正規化座標（0.0-1.0）かピクセル座標かを判定
                // cxが640を超えていれば既にピクセル座標、1.0以下なら正規化座標
                double cx_pixel, cy_pixel, w_pixel, h_pixel;
                if (cx > 1.0 || cy > 1.0 || w > 1.0 || h > 1.0) {
                    // 既にピクセル座標（640x640座標系）
                    cx_pixel = cx;
                    cy_pixel = cy;
                    w_pixel = w;
                    h_pixel = h;
                } else {
                    // 正規化座標（0.0-1.0）→ ピクセル座標に変換
                    cx_pixel = cx * inputSize;
                    cy_pixel = cy * inputSize;
                    w_pixel = w * inputSize;
                    h_pixel = h * inputSize;
                }
                
                // 2. パディングを考慮して元画像サイズに変換
                // パディング領域を除外
                double cx_unpadded = (cx_pixel - resizeInfo.offsetX) / resizeInfo.scale;
                double cy_unpadded = (cy_pixel - resizeInfo.offsetY) / resizeInfo.scale;
                double w_unpadded = w_pixel / resizeInfo.scale;
                double h_unpadded = h_pixel / resizeInfo.scale;
                
                // 3. 元画像サイズでのbbox座標を計算
                double x1 = cx_unpadded - w_unpadded / 2.0;
                double y1 = cy_unpadded - h_unpadded / 2.0;
                double x2 = cx_unpadded + w_unpadded / 2.0;
                double y2 = cy_unpadded + h_unpadded / 2.0;
                
                // 4. 画像範囲内にクリップ
                x1 = Math.max(0.0, Math.min(imageW, x1));
                y1 = Math.max(0.0, Math.min(imageH, y1));
                x2 = Math.max(0.0, Math.min(imageW, x2));
                y2 = Math.max(0.0, Math.min(imageH, y2));
                
                // 5. 有効なbboxかチェック
                if (x2 > x1 && y2 > y1 && w_unpadded > 0 && h_unpadded > 0) {
                    BBox bbox = new BBox(x1, y1, x2, y2);
                    candidates.add(new DetectionCandidate(bestClass, maxScore, bbox));
                    if (i < 5) {
                        System.out.println(String.format("    ✓ bbox有効: x1=%.1f y1=%.1f x2=%.1f y2=%.1f", x1, y1, x2, y2));
                    }
                } else {
                    filteredByBbox++;
                    if (i < 5) {
                        System.out.println(String.format("    ✗ bbox無効: x1=%.1f y1=%.1f x2=%.1f y2=%.1f (w=%.1f h=%.1f)", 
                            x1, y1, x2, y2, w_unpadded, h_unpadded));
                    }
                }
            } else {
                filteredByScore++;
            }
        }
        
        // デバッグ情報を表示
        System.out.println("[DEBUG] 後処理結果:");
        System.out.println("  スコア閾値通過: " + candidateCount);
        System.out.println("  スコア閾値で除外: " + filteredByScore);
        System.out.println("  bbox無効で除外: " + filteredByBbox);
        System.out.println("  NMS前の候補数: " + candidates.size());
        
        // NMS実行（同じクラスの重複をより積極的に抑制）
        List<DetectionCandidate> nmsResult = nonMaxSuppression(candidates, nmsIouThreshold, nmsIouThresholdSameClass);
        System.out.println("  NMS後の候補数: " + nmsResult.size());
        
        // 追加の重複除去：同じクラスで中心距離が近い場合も除去
        nmsResult = removeNearbyDuplicates(nmsResult);
        System.out.println("  重複除去後の候補数: " + nmsResult.size());
        
        // DetSymbolに変換（LabelMapを使用してクラスID → トークン変換）
        // 範囲外のクラスIDはスキップして警告を表示
        LabelMap labelMap = new LabelMap();
        List<DetSymbol> result = new ArrayList<>();
        int skippedInvalidClass = 0;
        
        for (DetectionCandidate cand : nmsResult) {
            if (!labelMap.isValidClassId(cand.classId)) {
                System.err.println(String.format("[WARN] 無効なクラスIDをスキップ: %d (スコア=%.3f, bbox=[%.1f,%.1f,%.1f,%.1f])", 
                    cand.classId, cand.score, cand.bbox.x1, cand.bbox.y1, cand.bbox.x2, cand.bbox.y2));
                skippedInvalidClass++;
                continue;
            }
            
            try {
                String cls = labelMap.getClassLabel(cand.classId);
                String token = labelMap.getToken(cls);
                result.add(new DetSymbol(cls, token, cand.score, cand.bbox));
            } catch (IllegalArgumentException e) {
                System.err.println(String.format("[WARN] クラスID %d の処理に失敗: %s (スコア=%.3f)", 
                    cand.classId, e.getMessage(), cand.score));
                skippedInvalidClass++;
            }
        }
        
        if (skippedInvalidClass > 0) {
            System.out.println(String.format("[WARN] 無効なクラスIDでスキップされた検出: %d件", skippedInvalidClass));
        }
        
        System.out.println("[DEBUG] 最終検出数: " + result.size());
        return result;
    }
    
    /**
     * NMS（Non-Maximum Suppression）実装
     * 同じクラスの重複をより積極的に抑制する
     * 
     * @param candidates 検出候補リスト
     * @param iouThreshold 異なるクラス間のIoU閾値
     * @param iouThresholdSameClass 同じクラス間のIoU閾値（より厳しく）
     * @return NMS後の検出結果
     */
    private List<DetectionCandidate> nonMaxSuppression(
            List<DetectionCandidate> candidates, 
            double iouThreshold, 
            double iouThresholdSameClass) {
        
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        // スコアでソート（降順）
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        
        List<DetectionCandidate> result = new ArrayList<>();
        boolean[] suppressed = new boolean[candidates.size()];
        
        for (int i = 0; i < candidates.size(); i++) {
            if (suppressed[i]) continue;
            
            DetectionCandidate current = candidates.get(i);
            result.add(current);
            
            // 残りの候補でIoUを計算して抑制
            for (int j = i + 1; j < candidates.size(); j++) {
                if (suppressed[j]) continue;
                
                DetectionCandidate other = candidates.get(j);
                double iou = computeIoU(current.bbox, other.bbox);
                
                // 同じクラスの場合はより厳しい閾値を使用
                double threshold = (current.classId == other.classId) 
                    ? iouThresholdSameClass 
                    : iouThreshold;
                
                if (iou > threshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }
    
    /**
     * IoU（Intersection over Union）を計算
     * 
     * @param box1 バウンディングボックス1
     * @param box2 バウンディングボックス2
     * @return IoU値（0.0-1.0）
     */
    private double computeIoU(BBox box1, BBox box2) {
        // 交差領域
        double x1 = Math.max(box1.x1, box2.x1);
        double y1 = Math.max(box1.y1, box2.y1);
        double x2 = Math.min(box1.x2, box2.x2);
        double y2 = Math.min(box1.y2, box2.y2);
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;  // 交差なし
        }
        
        double intersection = (x2 - x1) * (y2 - y1);
        double area1 = box1.w() * box1.h();
        double area2 = box2.w() * box2.h();
        double union = area1 + area2 - intersection;
        
        return union > 0 ? intersection / union : 0.0;
    }
    
    /**
     * 追加の重複除去：同じクラスで中心距離が近い場合も除去
     * NMSを通過した後でも、同じクラスで非常に近い位置にある検出を除去
     * 
     * @param candidates NMS後の検出候補リスト
     * @return 重複除去後の検出結果
     */
    private List<DetectionCandidate> removeNearbyDuplicates(List<DetectionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        
        // スコアでソート（降順）
        List<DetectionCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Double.compare(b.score, a.score));
        
        List<DetectionCandidate> result = new ArrayList<>();
        boolean[] removed = new boolean[sorted.size()];
        
        for (int i = 0; i < sorted.size(); i++) {
            if (removed[i]) continue;
            
            DetectionCandidate current = sorted.get(i);
            result.add(current);
            
            // 同じクラスの候補をチェック
            for (int j = i + 1; j < sorted.size(); j++) {
                if (removed[j]) continue;
                
                DetectionCandidate other = sorted.get(j);
                
                // 同じクラスの場合のみチェック
                if (current.classId == other.classId) {
                    // 中心座標を計算
                    double cx1 = (current.bbox.x1 + current.bbox.x2) / 2.0;
                    double cy1 = (current.bbox.y1 + current.bbox.y2) / 2.0;
                    double cx2 = (other.bbox.x1 + other.bbox.x2) / 2.0;
                    double cy2 = (other.bbox.y1 + other.bbox.y2) / 2.0;
                    
                    // 中心間距離を計算
                    double dx = cx2 - cx1;
                    double dy = cy2 - cy1;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    
                    // 平均ボックスサイズを計算（小さい方のサイズを基準）
                    double avgSize = Math.min(
                        Math.min(current.bbox.w(), current.bbox.h()),
                        Math.min(other.bbox.w(), other.bbox.h())
                    );
                    
                    // 中心距離が平均サイズの30%以下なら重複とみなす
                    if (distance < avgSize * 0.3) {
                        removed[j] = true;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 検出候補の内部クラス
     */
    private static class DetectionCandidate {
        final int classId;
        final double score;
        final BBox bbox;
        
        DetectionCandidate(int classId, double score, BBox bbox) {
            this.classId = classId;
            this.score = score;
            this.bbox = bbox;
        }
    }
    
    /**
     * リソースを解放
     */
    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
    
    /**
     * 信頼度閾値を設定
     */
    public void setConfidenceThreshold(double threshold) {
        this.confidenceThreshold = threshold;
    }
    
    /**
     * NMS IoU閾値を設定（異なるクラス間）
     */
    public void setNmsIouThreshold(double threshold) {
        this.nmsIouThreshold = threshold;
    }
    
    /**
     * 同じクラス間のNMS IoU閾値を設定
     */
    public void setNmsIouThresholdSameClass(double threshold) {
        this.nmsIouThresholdSameClass = threshold;
    }
}

