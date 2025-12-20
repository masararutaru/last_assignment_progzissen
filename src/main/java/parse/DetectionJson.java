package parse;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public class DetectionJson {

    public static Detection load(String path, LabelMap labelMap) throws Exception {
        return load(path, labelMap, null);
    }

    /**
     * 推論結果JSONを読み込む（warnings用のリストを渡せる版）
     * @param path JSONファイルのパス
     * @param labelMap クラス名→トークンマッピング
     * @param warnings 警告メッセージを追加するリスト（null可）
     * @return Detection オブジェクト
     */
    public static Detection load(String path, LabelMap labelMap, List<String> warnings) throws Exception {
        String txt = Files.readString(Paths.get(path));
        JSONObject root = new JSONObject(txt);

        JSONObject sz = root.getJSONObject("imageSize");
        int w = sz.getInt("w");
        int h = sz.getInt("h");

        JSONArray dets = root.getJSONArray("detections");
        List<DetSymbol> syms = new ArrayList<>();

        for (int i = 0; i < dets.length(); i++) {
            JSONObject d = dets.getJSONObject(i);
            String cls = d.getString("cls");
            double score = d.optDouble("score", 1.0);

            JSONArray bb = d.getJSONArray("bbox");
            BBox box = new BBox(bb.getDouble(0), bb.getDouble(1), bb.getDouble(2), bb.getDouble(3));

            try {
                String token = labelMap.toToken(cls);
                syms.add(new DetSymbol(cls, token, score, box));
            } catch (IllegalArgumentException e) {
                // 不明なクラス名の場合、警告を追加してスキップ
                if (warnings != null) {
                    warnings.add(String.format("unknown class label: '%s' (score=%.2f, bbox=[%.0f,%.0f,%.0f,%.0f]), skipped", 
                            cls, score, box.x1, box.y1, box.x2, box.y2));
                }
                // スキップ（このシンボルは追加しない）
            }
        }
        return new Detection(w, h, syms);
    }
}
