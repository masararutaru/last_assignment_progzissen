package parse;

public class BBox {
    public final double x1, y1, x2, y2;

    public BBox(double x1, double y1, double x2, double y2) {
        if (x2 < x1 || y2 < y1) throw new IllegalArgumentException("invalid bbox");
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    public double w() { return x2 - x1; }
    public double h() { return y2 - y1; }
    public double cx() { return (x1 + x2) * 0.5; }
    public double cy() { return (y1 + y2) * 0.5; }

    public boolean overlapsX(BBox other, double margin) {
        double a1 = this.x1 - margin, a2 = this.x2 + margin;
        double b1 = other.x1 - margin, b2 = other.x2 + margin;
        return Math.max(a1, b1) <= Math.min(a2, b2);
    }

    public boolean overlapsY(BBox other, double margin) {
        double a1 = this.y1 - margin, a2 = this.y2 + margin;
        double b1 = other.y1 - margin, b2 = other.y2 + margin;
        return Math.max(a1, b1) <= Math.min(a2, b2);
    }
}
