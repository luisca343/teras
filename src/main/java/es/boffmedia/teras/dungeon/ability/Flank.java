package es.boffmedia.teras.dungeon.ability;

/**
 * The rear-arc geometry behind {@link AbilityKind#FLANK_RAGE}, with no game types so it can be
 * tested on its own — {@code AbilityEngine} drags in half of Minecraft and never loads under JUnit.
 */
public final class Flank {
    private Flank() {}

    /**
     * Whether the attacker stands in the enemy's rear arc: inside {@code arcDegrees} of directly
     * behind the way {@code yawDegrees} faces.
     */
    public static boolean isRear(double selfX, double selfZ, float yawDegrees,
                                 double attackerX, double attackerZ, double arcDegrees) {
        double dx = attackerX - selfX;
        double dz = attackerZ - selfZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) {
            return false;
        }
        double yaw = Math.toRadians(yawDegrees);
        // cos of the angle between the facing (−sin, cos) and the direction to the attacker.
        double alignment = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
        return alignment <= Math.cos(Math.toRadians(180.0 - arcDegrees / 2.0));
    }
}
