package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.dungeon.gear.GearDef;
import es.boffmedia.teras.dungeon.gear.GearOp;
import es.boffmedia.teras.dungeon.gear.GearStat;

import java.util.List;

/**
 * How a piece of equipo's first-party stat lines reach the sheet.
 *
 * <h2>Why this exists as its own class</h2>
 *
 * <p>A gear line is authored in {@code gear.json} against {@link GearStat}; a fight is resolved
 * against {@link Stat}. Those are two vocabularies on purpose — the first is a catalogue key an admin
 * types, the second is an axis the engine reads — and the translation between them is the sort of
 * thing that is written once inside whichever caller needed it first and then quietly copied. It is
 * also, by itself, the reason a stat is live or inert: before this existed, {@code critico} was a
 * number nothing could set, so no hit in the dungeon could crit.</p>
 *
 * <p>Only <b>first-party</b> lines are handled here. A vanilla-backed line
 * ({@link GearStat#vanilla()}) already reached the entity as an attribute modifier and is read back
 * off the entity by {@code CombatSheets}; adding it here as well would count it twice.</p>
 *
 * <p>Pure — no Minecraft — so the mapping and the flat/scale split are testable without a server.
 * {@code CombatSheets} does the part that needs an entity: finding the worn stacks.</p>
 */
public final class GearSheet {
    private GearSheet() {}

    /**
     * The sheet axis a gear line moves, or null when the line is vanilla-backed and therefore
     * somebody else's job.
     */
    public static Stat statOf(GearStat gear) {
        if (gear == null || gear.vanilla()) {
            return null;
        }
        return switch (gear) {
            case CRITICO -> Stat.CRITICO;
            case CONTUNDENCIA -> Stat.CONTUNDENCIA;
            case PENETRACION -> Stat.PENETRACION;
            case ALCANCE -> Stat.ALCANCE;
            case ENFRIAMIENTO -> Stat.ENFRIAMIENTO;
            case APLOMO -> Stat.APLOMO;
            case SUERTE -> Stat.SUERTE;
            case ESCUDO -> Stat.ESCUDO;
            // Exhaustive over the vanilla-backed six, which statOf has already refused above. Listed
            // rather than defaulted so adding a GearStat fails to compile until it is mapped.
            case ATTACK_DAMAGE, ATTACK_SPEED, ARMOR, ARMOR_TOUGHNESS, MOVEMENT_SPEED, MAX_HEALTH
                    -> null;
        };
    }

    /**
     * Adds every first-party line in {@code lines} to {@code sheet}.
     *
     * <p>{@link GearOp#FLAT} adds to the total and {@link GearOp#FRACTION_OF_BASE} adds to the
     * multiplier, which is the same distinction the operation already means for a vanilla attribute —
     * so a line reads the same whichever kind of stat it names. Two pieces each saying
     * {@code fraction_of_base: 0.5} therefore make ×2.0 rather than ×2.25, per {@link StatBlock}.</p>
     */
    public static void apply(StatBlock sheet, List<GearDef.Stat> lines) {
        if (sheet == null || lines == null) {
            return;
        }
        for (GearDef.Stat line : lines) {
            if (line == null) {
                continue;
            }
            Stat stat = statOf(line.stat());
            if (stat == null) {
                continue;
            }
            if (line.operation() == GearOp.FRACTION_OF_BASE) {
                sheet.addScale(stat, line.amount());
            } else {
                sheet.addFlat(stat, line.amount());
            }
        }
    }
}
