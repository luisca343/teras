package es.boffmedia.teras.region.model;

import java.util.Objects;

/**
 * An XZ vertex of a region polygon. Field names ({@code x}, {@code z}) are the legacy
 * SmartRotom web shape ({@code PolygonCreator.Point} on 1.16.5), so the serialized form
 * stays consumable by the existing web map.
 */
public final class RegionPoint {
    private final int x;
    private final int z;

    public RegionPoint(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RegionPoint other && other.x == x && other.z == z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}
