package es.boffmedia.teras.dungeon.entity;

import java.util.List;
import java.util.Map;

/**
 * Where a multipart enemy's extra hit boxes sit, in blocks at scale 1.0.
 *
 * <p>Only the long arachnids need these: their abdomen reaches far behind the trunk that the main
 * box covers, so a swing at the abdomen lands on nothing. Each segment is one box along the body's
 * facing axis — {@code forward} is toward the head (the rigs all face local −z), {@code bottom} and
 * {@code height} give its vertical span above the feet, {@code width} its side-to-side size. Boxes
 * are laterally centred, which is why only {@code forward} carries a sign.</p>
 *
 * <p>Numbers come from the trunk geometry in {@code tools/preview_rig.py}; {@code RigGeometryTest}
 * checks the union of a variant's boxes and its main hitbox actually covers the model's depth. At
 * most {@link #MAX_SEGMENTS} per enemy — the entity allocates that many parts once and collapses
 * the unused ones, because it cannot grow the array after the variant is known on the client.</p>
 *
 * @param forward distance toward the head from the entity's feet, blocks at scale 1.0
 * @param width   side-to-side size, blocks at scale 1.0
 * @param bottom  box floor above the feet, blocks at scale 1.0
 * @param height  box height, blocks at scale 1.0
 */
public record EnemyHitboxParts(double forward, double width, double bottom, double height) {

    /** The most boxes any one enemy uses; the queen, with head, abdomen and spinneret. */
    public static final int MAX_SEGMENTS = 3;

    private static final Map<String, List<EnemyHitboxParts>> BY_VARIANT = Map.of(
            // Each box is about as wide as the body is at that point, so it covers the depth without
            // padding the sides. The abdomen of the two longer spiders takes two boxes for that
            // reason; one wide enough to span it would be hit from either flank.
            "reina_madre", List.of(
                    new EnemyHitboxParts(0.55, 0.50, 0.10, 0.62),
                    new EnemyHitboxParts(-0.82, 0.82, 0.12, 0.88),
                    new EnemyHitboxParts(-1.38, 0.30, 0.38, 0.24)),
            "cazadora", List.of(
                    new EnemyHitboxParts(0.44, 0.44, 0.22, 0.56),
                    new EnemyHitboxParts(-0.53, 0.50, 0.44, 0.50),
                    new EnemyHitboxParts(-0.90, 0.44, 0.44, 0.42)),
            "tejedora", List.of(
                    new EnemyHitboxParts(0.44, 0.40, 0.12, 0.50),
                    new EnemyHitboxParts(-0.60, 0.62, 0.16, 0.68),
                    new EnemyHitboxParts(-1.00, 0.50, 0.30, 0.40)),
            "cria", List.of(
                    new EnemyHitboxParts(0.38, 0.38, 0.12, 0.44),
                    new EnemyHitboxParts(-0.53, 0.50, 0.19, 0.50)));

    /** The boxes for a variant, empty if it is a single-box enemy. */
    public static List<EnemyHitboxParts> forVariant(String id) {
        return BY_VARIANT.getOrDefault(GeoEnemyVariant.current(id), List.of());
    }

    public static Map<String, List<EnemyHitboxParts>> all() {
        return BY_VARIANT;
    }

    /**
     * This box's horizontal offset from the entity's feet, in world blocks, at a body yaw. The rig
     * faces local −z and GeckoLib turns the model by {@code 180 − yaw}, which lands its forward at
     * the entity's facing; {@code {dx, dz}} is {@code forward} rotated to match.
     */
    public double[] worldOffset(double scale, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double f = forward * scale;
        return new double[] {-f * Math.sin(yaw), f * Math.cos(yaw)};
    }
}
