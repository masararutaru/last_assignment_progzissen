package parse;

import java.util.*;

public class Detection {
    public final int imageW, imageH;
    public final List<DetSymbol> symbols;

    public Detection(int imageW, int imageH, List<DetSymbol> symbols) {
        this.imageW = imageW;
        this.imageH = imageH;
        this.symbols = Collections.unmodifiableList(new ArrayList<>(symbols));
    }
}
