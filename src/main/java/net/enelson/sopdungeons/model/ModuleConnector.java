package net.enelson.sopdungeons.model;

public final class ModuleConnector {
    public enum Kind { ENTRY, EXIT, GENERIC }

    private final String id;
    private final Kind kind;
    private final RelativePoint point;
    private final String direction;

    public ModuleConnector(String id, Kind kind, RelativePoint point, String direction) {
        this.id = id;
        this.kind = kind;
        this.point = point;
        this.direction = direction;
    }

    public String getId() { return id; }
    public Kind getKind() { return kind; }
    public RelativePoint getPoint() { return point; }
    public String getDirection() { return direction; }

    public String getRotatedDirection(int rotation) {
        int turns = (((rotation % 360) + 360) % 360) / 90;
        String[] directions = {"NORTH", "EAST", "SOUTH", "WEST"};
        int index;
        if ("NORTH".equalsIgnoreCase(direction)) index = 0;
        else if ("EAST".equalsIgnoreCase(direction)) index = 1;
        else if ("SOUTH".equalsIgnoreCase(direction)) index = 2;
        else if ("WEST".equalsIgnoreCase(direction)) index = 3;
        else throw new IllegalArgumentException("Unknown connector direction: " + direction);
        return directions[(index + turns) % directions.length];
    }
}
