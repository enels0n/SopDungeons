package net.enelson.sopdungeons.model;

public final class RelativePoint {
    private final int x;
    private final int y;
    private final int z;

    public RelativePoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public RelativePoint rotate(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 90) return new RelativePoint(-z, y, x);
        if (normalized == 180) return new RelativePoint(-x, y, -z);
        if (normalized == 270) return new RelativePoint(z, y, -x);
        return this;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
