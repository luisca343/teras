package es.boffmedia.teras.dungeon.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules a finished enemy obeys, checked mechanically — {@code RoomAudit} for the bestiary.
 *
 * <p>Every fault it looks for has already shipped at least once, and each was invisible in play
 * rather than loud: {@code VOLLEY} and {@code BLINK} declared with no goal behind them, a clip
 * requested from a rig that never had it, a variant naming a texture that was not in the jar. All
 * of them read as configured everywhere a person would look, and all of them did nothing.</p>
 *
 * <p>Pure — no Minecraft, so the whole bestiary can be held to it in a unit test rather than only
 * by a server that has already started. Asset <i>existence</i> is the one question it cannot answer
 * alone, so it reports which paths a caller must confirm and the test resolves them off the
 * classpath.</p>
 */
public final class BestiaryAudit {
    private BestiaryAudit() {}

    public enum Level { ERROR, WARNING }

    public record Finding(Level level, String variant, String message) {
        @Override
        public String toString() {
            return (level == Level.ERROR ? "x " : "! ") + variant + ": " + message;
        }
    }

    /** Clips the entity's animation controller can ask any rig for, whatever the variant. */
    public static final List<String> UNIVERSAL_CLIPS = List.of("idle", "walk", "attack");

    /** Every finding for one variant. */
    public static List<Finding> audit(GeoEnemyVariant variant) {
        List<Finding> findings = new ArrayList<>();
        String id = variant.id();

        for (Behaviour behaviour : variant.behaviours()) {
            if (!behaviour.isImplemented()) {
                findings.add(new Finding(Level.ERROR, id, "declares " + behaviour
                        + ", which composes no goal — it will look configured and do nothing"));
            }
        }
        if (!variant.movement().isImplemented()) {
            findings.add(new Finding(Level.ERROR, id, "declares movement " + variant.movement()
                    + ", which nothing honours — it will move as GROUND"));
        }
        if (variant.behaviours().isEmpty()) {
            findings.add(new Finding(Level.WARNING, id,
                    "declares no behaviours; it falls back to plain melee"));
        }
        if (variant.shoots() && variant.rangedDamage() <= 0) {
            findings.add(new Finding(Level.ERROR, id,
                    "has a ranged behaviour but rangedDamage is " + variant.rangedDamage()
                            + " — it will shoot for nothing"));
        }
        if (variant.shoots() && variant.rangedCooldown() <= 0) {
            findings.add(new Finding(Level.WARNING, id,
                    "has a ranged behaviour with no cooldown; a floor is applied at spawn"));
        }
        if (!variant.shoots() && variant.rangedDamage() > 0
                && !variant.has(Behaviour.CEILING_WEB)) {
            findings.add(new Finding(Level.WARNING, id,
                    "sets rangedDamage but has no ranged behaviour to spend it"));
        }
        if (variant.scale() <= 0) {
            findings.add(new Finding(Level.ERROR, id, "scale is " + variant.scale()));
        }
        if (variant.health() <= 0) {
            findings.add(new Finding(Level.ERROR, id, "health must be positive"));
        }
        // Speed zero is a broken enemy everywhere except the one mode whose whole statement is that
        // it does not move. ROOTED is checked the other way round: the entity forces the attribute
        // to zero, so a rooted variant that declares a speed has written a number nothing reads.
        if (variant.movement() == Movement.ROOTED) {
            if (variant.speed() != 0) {
                findings.add(new Finding(Level.WARNING, id,
                        "is ROOTED but declares speed " + variant.speed()
                                + ", which the entity overrides to zero"));
            }
        } else if (variant.speed() <= 0) {
            findings.add(new Finding(Level.ERROR, id, "speed must be positive"));
        }
        // A hopper's speed is spent per bounce rather than per tick, so the usual walking values
        // read as motionless. Cheap to get wrong and impossible to see except in play.
        if (variant.movement() == Movement.HOPPER && variant.speed() < 0.2) {
            findings.add(new Finding(Level.WARNING, id, "a HOPPER at speed " + variant.speed()
                    + " barely closes; its speed is spent per bounce, not per tick"));
        }
        // A "CLIMBER with a ranged behaviour" warning lived here and has been removed. It read as
        // a real check and was not: it tested has(RANGED) while the spawner routes perches on
        // shoots(), so it never fired for the tejedora — the one shipped variant it describes —
        // and a tejedora leaving a ledge is the intended design anyway (§12: Infestadas has no
        // archer because its shooters climb). A check that misses the case it is about, and would
        // flag correct content if fixed, teaches people to ignore the audit.
        for (String path : List.of(variant.model(), variant.texture(), variant.animation())) {
            if (path == null || path.isBlank()) {
                findings.add(new Finding(Level.ERROR, id, "has an empty asset path"));
            }
        }
        return findings;
    }

    /** Every finding across the shipped bestiary, most severe first. */
    public static List<Finding> auditAll() {
        List<Finding> findings = new ArrayList<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            findings.addAll(audit(variant));
        }
        findings.sort((a, b) -> a.level().compareTo(b.level()));
        return findings;
    }

    /**
     * Every animation clip {@code variant} can be asked to play — the universal set plus one per
     * behaviour that drives a clip, plus the climb loop a climber's movement drives.
     *
     * <p>This is the list a rig's {@code .animation.json} must satisfy. It is derived from the
     * variant rather than written down per rig, so adding a behaviour that plays a new clip
     * immediately obliges every rig that carries it.</p>
     */
    public static Set<String> requiredClips(GeoEnemyVariant variant) {
        Set<String> clips = new LinkedHashSet<>(UNIVERSAL_CLIPS);
        for (Behaviour behaviour : variant.behaviours()) {
            String clip = behaviour.clip();
            if (!clip.isEmpty()) {
                clips.add(clip);
            }
        }
        if (variant.movement() == Movement.CLIMBER) {
            clips.add("climb");
        }
        if (variant.movement() == Movement.HOPPER) {
            clips.add("jump");
        }
        return clips;
    }

    /** True when nothing would break in play; warnings do not count. */
    public static boolean clean(List<Finding> findings) {
        return findings.stream().noneMatch(f -> f.level() == Level.ERROR);
    }
}
