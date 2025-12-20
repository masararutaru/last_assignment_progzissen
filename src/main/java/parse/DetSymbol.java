package parse;

public class DetSymbol {
    public final String cls;     // raw class label (e.g., "digit_3" or "+" or "sin")
    public final String token;   // normalized token used in expression string (e.g., "3" or "+" or "sin")
    public final double score;
    public final BBox box;

    public DetSymbol(String cls, String token, double score, BBox box) {
        this.cls = cls;
        this.token = token;
        this.score = score;
        this.box = box;
    }
}
