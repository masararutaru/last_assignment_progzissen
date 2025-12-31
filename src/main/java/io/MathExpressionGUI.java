package io;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;

import parse.*;
import ast.*;

/**
 * 数式認識GUIアプリケーション
 * Canvasに数式を描いて推論し、式推論・認識・計算結果を表示
 */
public class MathExpressionGUI extends Frame implements ActionListener {
    
    private static final String MODEL_PATH = "assets/model_v3.onnx";
    private static final String OUTPUT_DIR = "samples/images";
    
    // GUIコンポーネント
    private Button btnClear;
    private Button btnInference;
    private Button btnUndo;
    private Button btnShowDetection;
    private Checkbox checkboxEraser;
    private Panel controlPanel;
    private DrawingCanvas drawingCanvas;
    private TextArea resultArea;
    private ScrollPane resultScrollPane;
    private Panel variableInputPanel;
    
    // データ
    private OnnxInference inference;
    private Detection lastDetection;  // 最後の推論結果を保持
    private BufferedImage lastCanvasImage;  // 最後のキャンバス画像を保持
    private VariableContext variableContext;
    private java.util.Map<String, TextField> variableFields;
    private Expr lastParsedExpr;  // 最後にパースした式を保持
    
    public static void main(String[] args) {
        new MathExpressionGUI();
    }
    
    public MathExpressionGUI() {
        super("数式認識アプリケーション");
        this.setSize(2000, 1100);
        
        // レイアウト設定
        this.setLayout(new BorderLayout(10, 10));
        
        // コントロールパネル（上部）
        controlPanel = new Panel();
        controlPanel.setLayout(new FlowLayout());
        
        // ボタンのフォントを大きくしてボタンサイズを拡大
        Font buttonFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        
        btnClear = new Button("クリア");
        btnClear.setFont(buttonFont);
        btnClear.addActionListener(this);
        btnInference = new Button("推論");
        btnInference.setFont(buttonFont);
        btnInference.addActionListener(this);
        btnUndo = new Button("Undo");
        btnUndo.setFont(buttonFont);
        btnUndo.addActionListener(this);
        btnShowDetection = new Button("検出領域を表示");
        btnShowDetection.setFont(buttonFont);
        btnShowDetection.addActionListener(this);
        btnShowDetection.setEnabled(false);  // 初期状態では無効
        checkboxEraser = new Checkbox("消しゴムモード");
        checkboxEraser.setFont(buttonFont);
        checkboxEraser.addItemListener(e -> {
            drawingCanvas.setEraserMode(checkboxEraser.getState());
        });
        controlPanel.add(btnClear);
        controlPanel.add(btnInference);
        controlPanel.add(btnUndo);
        controlPanel.add(btnShowDetection);
        controlPanel.add(checkboxEraser);
        this.add(controlPanel, BorderLayout.NORTH);
        
        // 描画用Canvas（中央）
        drawingCanvas = new DrawingCanvas();
        this.add(drawingCanvas, BorderLayout.CENTER);
        
        // 変数コンテキストと変数入力パネルを初期化
        variableContext = new VariableContext();
        variableFields = new java.util.HashMap<>();
        variableInputPanel = new Panel();
        variableInputPanel.setLayout(new GridBagLayout());
        variableInputPanel.setPreferredSize(new Dimension(800, 200));
        
        // 結果表示エリア（右側）- 大きく、フォントも大きく
        resultArea = new TextArea("", 50, 150, TextArea.SCROLLBARS_VERTICAL_ONLY);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        resultScrollPane = new ScrollPane();
        resultScrollPane.add(resultArea);
        // 推論結果エリアをより大きく表示するため、幅を指定
        resultScrollPane.setPreferredSize(new Dimension(800, 0));
        
        // 結果表示エリアと変数入力パネルをまとめるパネル
        Panel rightPanel = new Panel();
        rightPanel.setLayout(new BorderLayout());
        rightPanel.add(resultScrollPane, BorderLayout.CENTER);
        rightPanel.add(variableInputPanel, BorderLayout.SOUTH);
        this.add(rightPanel, BorderLayout.EAST);
        
        // ウィンドウクローズ処理
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (inference != null) {
                    try {
                        inference.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                System.exit(0);
            }
        });
        
        this.setVisible(true);
        
        // ONNXモデルの読み込み
        try {
            inference = new OnnxInference(MODEL_PATH);
            resultArea.append("モデルを読み込みました: " + MODEL_PATH + "\n");
            resultArea.append("Canvasに数式を描いて「推論」ボタンを押してください\n\n");
        } catch (Exception e) {
            resultArea.append("エラー: モデルの読み込みに失敗しました\n");
            resultArea.append(e.getMessage() + "\n");
            e.printStackTrace();
            btnInference.setEnabled(false);
        }
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnClear) {
            drawingCanvas.clearCanvas();
            lastDetection = null;
            lastCanvasImage = null;
            btnShowDetection.setEnabled(false);
        } else if (e.getSource() == btnInference) {
            performInference();
        } else if (e.getSource() == btnUndo) {
            drawingCanvas.undo();
        } else if (e.getSource() == btnShowDetection) {
            showDetectionAreas();
        }
    }
    
    /**
     * 推論を実行
     */
    private void performInference() {
        if (inference == null) {
            resultArea.append("エラー: モデルが読み込まれていません\n");
            return;
        }
        
        // Canvasの内容を画像として取得
        BufferedImage canvasImage = drawingCanvas.getImage();
        if (canvasImage == null) {
            resultArea.append("エラー: Canvasの画像を取得できませんでした\n");
            return;
        }
        
        // 出力ディレクトリを作成
        File outputDir = new File(OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 画像を保存
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "drawn_" + timestamp + ".png";
        File imageFile = new File(outputDir, fileName);
        
        try {
            ImageIO.write(canvasImage, "png", imageFile);
            // 画像保存メッセージは表示しない
        } catch (Exception e) {
            resultArea.append("エラー: 画像の保存に失敗しました: " + e.getMessage() + "\n");
            e.printStackTrace();
            return;
        }
        
        resultArea.append("\n");
        resultArea.append("═══════════════════════════════════════════════════\n");
        resultArea.append("                   推論開始\n");
        resultArea.append("═══════════════════════════════════════════════════\n");
        resultArea.append("\n");
        
        try {
            int imageW = canvasImage.getWidth();
            int imageH = canvasImage.getHeight();
            
            // 1. ONNX推論を実行
            resultArea.append("推論を実行中...\n");
            Detection detection = inference.detect(canvasImage, imageW, imageH);
            
            // 検出結果を保持
            lastDetection = detection;
            lastCanvasImage = canvasImage;
            btnShowDetection.setEnabled(true);  // 検出領域表示ボタンを有効化
            
            resultArea.append("検出シンボル数: " + detection.symbols.size() + "\n");
            
            // デバッグ情報: 検出されたシンボルとスコアを表示
            if (detection.symbols.size() > 0) {
                double minScore = detection.symbols.stream().mapToDouble(s -> s.score).min().orElse(0.0);
                double maxScore = detection.symbols.stream().mapToDouble(s -> s.score).max().orElse(0.0);
                double avgScore = detection.symbols.stream().mapToDouble(s -> s.score).average().orElse(0.0);
                resultArea.append(String.format("スコア範囲: %.3f - %.3f (平均: %.3f)\n", minScore, maxScore, avgScore));
                
                // 最初の10個のシンボルを表示
                int showCount = Math.min(10, detection.symbols.size());
                resultArea.append("検出されたシンボル（最初の" + showCount + "個）:\n");
                for (int i = 0; i < showCount; i++) {
                    DetSymbol sym = detection.symbols.get(i);
                    resultArea.append(String.format("  %d: %s (スコア=%.3f)\n", i + 1, sym.token, sym.score));
                }
                if (detection.symbols.size() > showCount) {
                    resultArea.append("  ... 他 " + (detection.symbols.size() - showCount) + " 個\n");
                }
            }
            resultArea.append("\n");
            
            // 2. SpatialToExprで式文字列に変換（推論した式）
            SpatialToExpr spatialToExpr = new SpatialToExpr();
            SpatialToExpr.Result spatialResult = spatialToExpr.buildExprString(detection);
            
            String inferredExpr = spatialResult.expr;
            resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            resultArea.append("【式推論した式】\n");
            resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            resultArea.append(inferredExpr + "\n\n");
            
            // 警告があれば表示（括弧の修正情報も含む）
            if (!spatialResult.warnings.isEmpty()) {
                resultArea.append("⚠ 警告・情報:\n");
                for (String warn : spatialResult.warnings) {
                    resultArea.append("  - " + warn + "\n");
                }
                resultArea.append("\n");
            }
            
            // 3. 式をパース（認識した式）
            if (inferredExpr.isEmpty()) {
                resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                resultArea.append("【認識した式】\n");
                resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                resultArea.append("（式が空です）\n\n");
                resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                resultArea.append("【計算結果】\n");
                resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                resultArea.append("（計算できません）\n\n");
            } else {
                try {
                    Expr expr = Parser.parse(inferredExpr);
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("【認識した式】\n");
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("✓ パース成功\n");
                    resultArea.append("式: " + inferredExpr + "\n\n");
                    
                    // 4. 計算結果（変数入力対応版）
                    // 式に含まれる変数を抽出
                    java.util.Set<String> variables = VariableExtractor.extractVariables(expr);
                    lastParsedExpr = expr;  // 式を保持
                    
                    // 変数入力パネルを更新
                    updateVariableInputPanel(variables);
                    
                    // 変数コンテキストを更新
                    updateVariableContext();
                    
                    // 変数コンテキストをSymに設定
                    Sym.setGlobalContext(variableContext);
                    
                    // 計算実行（xパラメータは使用しないが、互換性のため残す）
                    double value = expr.eval(1.0);
                    
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("【計算結果】\n");
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    
                    if (!variables.isEmpty()) {
                        resultArea.append("変数の値:\n");
                        for (String varName : variables) {
                            double varValue = variableContext.getVariable(varName);
                            resultArea.append("  " + varName + " = " + varValue + "\n");
                        }
                        resultArea.append("\n");
                    }
                    
                    resultArea.append("答え = " + value + "\n\n");
                    
                } catch (Exception parseEx) {
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("【認識した式】\n");
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    String errorMsg = parseEx.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = parseEx.getClass().getSimpleName();
                    }
                    resultArea.append("✗ パースエラー: " + errorMsg + "\n");
                    resultArea.append("式: " + inferredExpr + "\n");
                    // スタックトレースの最初の数行を表示
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    parseEx.printStackTrace(pw);
                    String stackTrace = sw.toString();
                    String[] lines = stackTrace.split("\n");
                    int showLines = Math.min(5, lines.length);
                    resultArea.append("\nスタックトレース（最初の" + showLines + "行）:\n");
                    for (int k = 0; k < showLines; k++) {
                        resultArea.append("  " + lines[k] + "\n");
                    }
                    resultArea.append("\n");
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("【計算結果】\n");
                    resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    resultArea.append("（計算できません）\n\n");
                    parseEx.printStackTrace();
                }
            }
            
            resultArea.append("═══════════════════════════════════════════════════\n");
            resultArea.append("                   推論完了\n");
            resultArea.append("═══════════════════════════════════════════════════\n");
            resultArea.append("\n");
            
        } catch (Exception e) {
            resultArea.append("エラー: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
    
    /**
     * 検出領域を表示するウィンドウを開く
     */
    private void showDetectionAreas() {
        if (lastDetection == null || lastCanvasImage == null) {
            resultArea.append("エラー: 検出結果がありません。先に推論を実行してください。\n");
            return;
        }
        
        // 検出結果を描画した画像を作成
        BufferedImage displayImage = new BufferedImage(
            lastCanvasImage.getWidth(),
            lastCanvasImage.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g2d = displayImage.createGraphics();
        
        // 元画像を描画
        g2d.drawImage(lastCanvasImage, 0, 0, null);
        
        // 検出されたbboxを描画
        g2d.setStroke(new BasicStroke(3.0f));
        Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
        g2d.setFont(labelFont);
        
        // 色のリスト（各クラスに異なる色を割り当て）
        Color[] colors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.MAGENTA,
            Color.CYAN, Color.YELLOW, Color.PINK, Color.LIGHT_GRAY, Color.DARK_GRAY
        };
        
        for (int i = 0; i < lastDetection.symbols.size(); i++) {
            DetSymbol sym = lastDetection.symbols.get(i);
            parse.BBox bbox = sym.box;  // DetSymbolのboxフィールドを使用
            
            // 色を選択（クラスIDに基づいて）
            Color color = colors[i % colors.length];
            g2d.setColor(color);
            
            // bboxを描画
            int x1 = (int) Math.round(bbox.x1);
            int y1 = (int) Math.round(bbox.y1);
            int x2 = (int) Math.round(bbox.x2);
            int y2 = (int) Math.round(bbox.y2);
            
            // 矩形を描画
            g2d.drawRect(x1, y1, x2 - x1, y2 - y1);
            
            // 中心点を描画
            int cx = (int) Math.round(bbox.cx());
            int cy = (int) Math.round(bbox.cy());
            g2d.fillOval(cx - 3, cy - 3, 6, 6);
            
            // ラベル（クラス名、スコア、座標情報）を描画
            String label = String.format("%s (%.2f)", sym.token, sym.score);
            String coordLabel = String.format("[%.0f,%.0f,%.0f,%.0f] cx=%.0f cy=%.0f", 
                bbox.x1, bbox.y1, bbox.x2, bbox.y2, bbox.cx(), bbox.cy());
            
            // ラベルの背景を描画
            FontMetrics fm = g2d.getFontMetrics();
            int labelWidth = Math.max(fm.stringWidth(label), fm.stringWidth(coordLabel));
            int labelHeight = fm.getHeight();
            
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
            g2d.fillRect(x1, y1 - labelHeight * 2 - 4, labelWidth + 4, labelHeight * 2 + 4);
            
            // ラベルのテキストを描画
            g2d.setColor(Color.WHITE);
            g2d.drawString(label, x1 + 2, y1 - labelHeight - 2);
            g2d.drawString(coordLabel, x1 + 2, y1 - 2);
        }
        
        g2d.dispose();
        
        // 新しいウィンドウで表示
        Frame detectionFrame = new Frame("検出領域の表示（デバッグ用）");
        // 詳細情報を表示するためにウィンドウを大きくする
        detectionFrame.setSize(lastCanvasImage.getWidth() + 600, lastCanvasImage.getHeight() + 150);
        detectionFrame.setLayout(new BorderLayout());
        
        Canvas detectionCanvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                g.drawImage(displayImage, 0, 0, this);
            }
        };
        detectionCanvas.setSize(lastCanvasImage.getWidth(), lastCanvasImage.getHeight());
        
        // 詳細情報を表示するテキストエリアを追加
        TextArea detailArea = new TextArea("", 20, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setEditable(false);
        
        // 詳細情報を構築
        StringBuilder detailText = new StringBuilder();
        detailText.append("検出数: ").append(lastDetection.symbols.size()).append(" 個\n\n");
        detailText.append("詳細情報:\n");
        detailText.append(String.format("%-5s %-10s %-8s %-15s %-15s %-15s %-15s\n", 
            "番号", "トークン", "スコア", "x1,y1", "x2,y2", "中心(cx,cy)", "サイズ(w,h)"));
        detailText.append("────────────────────────────────────────────────────────────────────────────\n");
        
        for (int i = 0; i < lastDetection.symbols.size(); i++) {
            DetSymbol sym = lastDetection.symbols.get(i);
            parse.BBox bbox = sym.box;
            detailText.append(String.format("%-5d %-10s %-8.3f %-15s %-15s %-15s %-15s\n",
                i + 1,
                sym.token,
                sym.score,
                String.format("%.1f,%.1f", bbox.x1, bbox.y1),
                String.format("%.1f,%.1f", bbox.x2, bbox.y2),
                String.format("%.1f,%.1f", bbox.cx(), bbox.cy()),
                String.format("%.1f,%.1f", bbox.w(), bbox.h())
            ));
        }
        
        detailArea.setText(detailText.toString());
        
        Panel infoPanel = new Panel();
        infoPanel.setLayout(new BorderLayout());
        
        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new FlowLayout());
        Label infoLabel = new Label("検出数: " + lastDetection.symbols.size() + " 個");
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        buttonPanel.add(infoLabel);
        
        Button closeButton = new Button("閉じる");
        closeButton.addActionListener(e -> detectionFrame.dispose());
        buttonPanel.add(closeButton);
        
        // レイアウトを変更：画像と詳細情報を横並びに
        Panel mainPanel = new Panel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(detectionCanvas, BorderLayout.CENTER);
        mainPanel.add(detailArea, BorderLayout.EAST);
        
        detectionFrame.add(mainPanel, BorderLayout.CENTER);
        detectionFrame.add(buttonPanel, BorderLayout.SOUTH);
        
        detectionFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                detectionFrame.dispose();
            }
        });
        
        detectionFrame.setVisible(true);
    }
    
    /**
     * 変数入力パネルを更新
     */
    private void updateVariableInputPanel(java.util.Set<String> variables) {
        variableInputPanel.removeAll();
        variableFields.clear();
        
        if (variables.isEmpty()) {
            Label noVarLabel = new Label("変数はありません");
            noVarLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            variableInputPanel.add(noVarLabel);
        } else {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            Label titleLabel = new Label("変数の値を入力してください（デフォルト: 1.0）:");
            titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            variableInputPanel.add(titleLabel, gbc);
            
            int row = 1;
            for (String varName : variables) {
                Label varLabel = new Label(varName + " =");
                varLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 1;
                variableInputPanel.add(varLabel, gbc);
                
                TextField varField = new TextField("1.0", 10);
                varField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                varField.addActionListener(e -> {
                    updateVariableContext();
                    recalculateResult();
                });
                varField.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        updateVariableContext();
                        recalculateResult();
                    }
                });
                gbc.gridx = 1;
                gbc.gridy = row;
                variableInputPanel.add(varField, gbc);
                
                variableFields.put(varName, varField);
                row++;
            }
            
            Button recalcButton = new Button("再計算");
            recalcButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            recalcButton.addActionListener(e -> {
                updateVariableContext();
                recalculateResult();
            });
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            variableInputPanel.add(recalcButton, gbc);
        }
        
        variableInputPanel.validate();
        variableInputPanel.repaint();
    }
    
    /**
     * 変数コンテキストを更新
     */
    private void updateVariableContext() {
        variableContext.clear();
        for (java.util.Map.Entry<String, TextField> entry : variableFields.entrySet()) {
            String varName = entry.getKey();
            String valueStr = entry.getValue().getText();
            try {
                double value = Double.parseDouble(valueStr);
                variableContext.setVariable(varName, value);
            } catch (NumberFormatException e) {
                // 無効な値の場合はデフォルト値1.0を使用
                variableContext.setVariable(varName, 1.0);
            }
        }
    }
    
    /**
     * 計算結果を再計算して表示
     */
    private void recalculateResult() {
        if (lastParsedExpr == null) {
            return;
        }
        
        try {
            // 変数コンテキストをSymに設定
            Sym.setGlobalContext(variableContext);
            
            // 再計算
            double value = lastParsedExpr.eval(1.0);
            
            // 結果エリアの最後の計算結果部分を更新
            String currentText = resultArea.getText();
            int lastResultIndex = currentText.lastIndexOf("【計算結果】");
            if (lastResultIndex >= 0) {
                // 最後の計算結果部分を削除
                String beforeResult = currentText.substring(0, lastResultIndex);
                resultArea.setText(beforeResult);
            }
            
            // 新しい計算結果を追加
            resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            resultArea.append("【計算結果】\n");
            resultArea.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            
            java.util.Set<String> variables = VariableExtractor.extractVariables(lastParsedExpr);
            if (!variables.isEmpty()) {
                resultArea.append("変数の値:\n");
                for (String varName : variables) {
                    double varValue = variableContext.getVariable(varName);
                    resultArea.append("  " + varName + " = " + varValue + "\n");
                }
                resultArea.append("\n");
            }
            
            resultArea.append("答え = " + value + "\n\n");
            
            // スクロールを最下部に
            resultArea.setCaretPosition(resultArea.getText().length());
        } catch (Exception e) {
            resultArea.append("再計算エラー: " + e.getMessage() + "\n");
        }
    }
    
    /**
     * 描画用Canvas（DrawingApli01のMyCanvasを参考）
     */
    class DrawingCanvas extends Canvas implements MouseListener, MouseMotionListener {
        private int x, y;
        private int px, py;
        private Image img = null;
        private Graphics2D gc = null;
        private Dimension d;
        private boolean eraserMode = false;
        private java.util.Stack<Image> undoStack = new java.util.Stack<>();
        private static final int MAX_UNDO = 10;
        
        public DrawingCanvas() {
            this.setSize(1000, 700);
            this.setBackground(Color.WHITE);
            addMouseListener(this);
            addMouseMotionListener(this);
        }
        
        public void setEraserMode(boolean enabled) {
            this.eraserMode = enabled;
        }
        
        public void undo() {
            if (!undoStack.isEmpty()) {
                img = undoStack.pop();
                if (img != null) {
                    gc = (Graphics2D) img.getGraphics();
                    gc.setColor(eraserMode ? Color.WHITE : Color.BLACK);
                }
                repaint();
            }
        }
        
        private void saveState() {
            if (img != null) {
                // 現在の状態をコピーして保存
                BufferedImage copy = new BufferedImage(img.getWidth(this), img.getHeight(this), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = copy.createGraphics();
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight());
                g2d.drawImage(img, 0, 0, this);
                g2d.dispose();
                
                undoStack.push(copy);
                // 最大数を超えたら古いものを削除
                while (undoStack.size() > MAX_UNDO) {
                    undoStack.remove(0);
                }
            }
        }
        
        /**
         * Canvasの内容をBufferedImageとして取得
         */
        public BufferedImage getImage() {
            d = getSize();
            if (img == null) {
                return null;
            }
            
            // ImageをBufferedImageに変換
            BufferedImage bufferedImage = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, d.width, d.height);
            g2d.drawImage(img, 0, 0, this);
            g2d.dispose();
            
            return bufferedImage;
        }
        
        /**
         * Canvasをクリア
         */
        public void clearCanvas() {
            d = getSize();
            if (img == null) {
                img = createImage(d.width, d.height);
                gc = (Graphics2D) img.getGraphics();
            }
            if (gc == null) {
                gc = (Graphics2D) img.getGraphics();
            }
            gc.setColor(Color.WHITE);
            gc.fillRect(0, 0, d.width, d.height);
            gc.setColor(Color.BLACK);
            repaint();
        }
        
        @Override
        public void update(Graphics g) {
            paint(g);
        }
        
        @Override
        public void paint(Graphics g) {
            d = getSize();
            if (img == null) {
                img = createImage(d.width, d.height);
                gc = (Graphics2D) img.getGraphics();
                gc.setColor(Color.WHITE);
                gc.fillRect(0, 0, d.width, d.height);
                gc.setColor(Color.BLACK);
            }
            if (gc == null) {
                gc = (Graphics2D) img.getGraphics();
                gc.setColor(Color.WHITE);
                gc.fillRect(0, 0, d.width, d.height);
                gc.setColor(Color.BLACK);
            }
            g.drawImage(img, 0, 0, this);
        }
        
        @Override
        public void mouseClicked(MouseEvent e) {}
        
        @Override
        public void mouseEntered(MouseEvent e) {}
        
        @Override
        public void mouseExited(MouseEvent e) {}
        
        @Override
        public void mousePressed(MouseEvent e) {
            x = e.getX();
            y = e.getY();
            saveState();
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (gc == null) {
                d = getSize();
                if (img == null) {
                    img = createImage(d.width, d.height);
                    gc = (Graphics2D) img.getGraphics();
                    gc.setColor(Color.WHITE);
                    gc.fillRect(0, 0, d.width, d.height);
                    gc.setColor(Color.BLACK);
                } else {
                    gc = (Graphics2D) img.getGraphics();
                    gc.setColor(eraserMode ? Color.WHITE : Color.BLACK);
                }
            }
            
            // 消しゴムモードの場合は白で、通常モードの場合は黒で描画
            gc.setColor(eraserMode ? Color.WHITE : Color.BLACK);
            
            // 線の太さを設定（消しゴムモードの場合は少し太めに）
            float strokeWidth = eraserMode ? 15.0f : 3.0f;
            gc.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            px = x;
            py = y;
            x = e.getX();
            y = e.getY();
            gc.drawLine(px, py, x, y);
            repaint();
        }
        
        @Override
        public void mouseMoved(MouseEvent e) {}
    }
}

