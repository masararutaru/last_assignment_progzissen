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
    
    private static final String MODEL_PATH = "assets/model.onnx";
    private static final String OUTPUT_DIR = "samples/images";
    
    // GUIコンポーネント
    private Button btnClear;
    private Button btnInference;
    private Button btnUndo;
    private Checkbox checkboxEraser;
    private Panel controlPanel;
    private DrawingCanvas drawingCanvas;
    private TextArea resultArea;
    private ScrollPane resultScrollPane;
    
    // データ
    private OnnxInference inference;
    
    public static void main(String[] args) {
        new MathExpressionGUI();
    }
    
    public MathExpressionGUI() {
        super("数式認識アプリケーション");
        this.setSize(1600, 900);
        
        // レイアウト設定
        this.setLayout(new BorderLayout(10, 10));
        
        // コントロールパネル（上部）
        controlPanel = new Panel();
        controlPanel.setLayout(new FlowLayout());
        btnClear = new Button("クリア");
        btnClear.addActionListener(this);
        btnInference = new Button("推論");
        btnInference.addActionListener(this);
        btnUndo = new Button("Undo");
        btnUndo.addActionListener(this);
        checkboxEraser = new Checkbox("消しゴムモード");
        checkboxEraser.addItemListener(e -> {
            drawingCanvas.setEraserMode(checkboxEraser.getState());
        });
        controlPanel.add(btnClear);
        controlPanel.add(btnInference);
        controlPanel.add(btnUndo);
        controlPanel.add(checkboxEraser);
        this.add(controlPanel, BorderLayout.NORTH);
        
        // 描画用Canvas（中央）
        drawingCanvas = new DrawingCanvas();
        this.add(drawingCanvas, BorderLayout.CENTER);
        
        // 結果表示エリア（右側）
        resultArea = new TextArea("", 30, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultScrollPane = new ScrollPane();
        resultScrollPane.add(resultArea);
        this.add(resultScrollPane, BorderLayout.EAST);
        
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
        } else if (e.getSource() == btnInference) {
            performInference();
        } else if (e.getSource() == btnUndo) {
            drawingCanvas.undo();
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
            resultArea.append("画像を保存しました: " + imageFile.getAbsolutePath() + "\n");
        } catch (Exception e) {
            resultArea.append("エラー: 画像の保存に失敗しました: " + e.getMessage() + "\n");
            e.printStackTrace();
            return;
        }
        
        resultArea.append("=== 推論開始 ===\n");
        
        try {
            int imageW = canvasImage.getWidth();
            int imageH = canvasImage.getHeight();
            
            // 1. ONNX推論を実行
            resultArea.append("推論を実行中...\n");
            Detection detection = inference.detect(canvasImage, imageW, imageH);
            resultArea.append("検出シンボル数: " + detection.symbols.size() + "\n\n");
            
            // 2. SpatialToExprで式文字列に変換（推論した式）
            SpatialToExpr spatialToExpr = new SpatialToExpr();
            SpatialToExpr.Result spatialResult = spatialToExpr.buildExprString(detection);
            
            String inferredExpr = spatialResult.expr;
            resultArea.append("【式推論した式】\n");
            resultArea.append(inferredExpr + "\n\n");
            
            // 警告があれば表示
            if (!spatialResult.warnings.isEmpty()) {
                resultArea.append("警告:\n");
                for (String warn : spatialResult.warnings) {
                    resultArea.append("  - " + warn + "\n");
                }
                resultArea.append("\n");
            }
            
            // 3. 式をパース（認識した式）
            if (inferredExpr.isEmpty()) {
                resultArea.append("【認識した式】\n");
                resultArea.append("（式が空です）\n\n");
                resultArea.append("【計算結果】\n");
                resultArea.append("（計算できません）\n\n");
            } else {
                try {
                    Expr expr = Parser.parse(inferredExpr);
                    resultArea.append("【認識した式】\n");
                    resultArea.append("パース成功: " + inferredExpr + "\n");
                    resultArea.append("（AST構築完了）\n\n");
                    
                    // 4. 計算結果（x=1.0で評価）
                    // 式にxが含まれているかチェック
                    boolean hasX = inferredExpr.contains("x");
                    double x = 1.0;
                    double value = expr.eval(x);
                    resultArea.append("【計算結果】\n");
                    if (hasX) {
                        resultArea.append("x = " + x + " のとき\n");
                    }
                    resultArea.append("値 = " + value + "\n\n");
                    
                } catch (Exception parseEx) {
                    resultArea.append("【認識した式】\n");
                    resultArea.append("パースエラー: " + parseEx.getMessage() + "\n");
                    resultArea.append("（式: " + inferredExpr + "）\n\n");
                    resultArea.append("【計算結果】\n");
                    resultArea.append("（計算できません）\n\n");
                    parseEx.printStackTrace();
                }
            }
            
            resultArea.append("=== 推論完了 ===\n\n");
            
        } catch (Exception e) {
            resultArea.append("エラー: " + e.getMessage() + "\n");
            e.printStackTrace();
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

