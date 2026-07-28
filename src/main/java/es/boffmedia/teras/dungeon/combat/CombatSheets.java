package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.dungeon.gear.GearCurios;
import es.boffmedia.teras.dungeon.gear.GearDef;
import es.boffmedia.teras.dungeon.gear.GearHolder;
import es.boffmedia.teras.dungeon.gear.GearVanilla;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Where a combatant's {@link StatBlock} comes from.
 *
 * <h2>Two sources, and the line between them</h2>
 *
 * <p><b>Vanilla-backed axes are read off the entity.</b> Damage, armour, cadence, speed and
 * containers all already exist as attributes, and everything the shipped systems do lands there: a
 * gear piece's stat lines through {@code GearStamp}, an enemy's definition through its spawn, a devil
 * deal's sold hearts through its modifier. Reading the totals means the rebuilt pipeline starts where
 * the shipped balance already is, instead of making every enemy in the game wrong on the day it
 * switches on.</p>
 *
 * <p><b>First-party axes are read off the worn gear.</b> Crit, penetration, reach, cooldown rate,
 * poise, luck, greed and escudo have no vanilla attribute to live in, so {@link GearSheet} maps them
 * from each worn piece's stat lines. Until that existed they had no source at all — {@code critico}
 * sat at its base of zero, so <i>no hit in the dungeon could ever crit</i>, the crit branch in
 * {@link DamageMath} was unreachable, and the panel showed ten numbers that could not move.</p>
 *
 * <h2>Still a bridge, in one respect</h2>
 *
 * <p>ROGUELIKE stage 2 gives every <b>enemy</b> an authored sheet. Until then an enemy's block is
 * whatever its attributes say, because it wears no equipo — so the split above is in practice
 * "players have both halves, enemies have the first". <b>The replacement point is this class and
 * nowhere else:</b> everything downstream asks for a {@code StatBlock} and does not care where it came
 * from, so authoring a real bestiary is a change here plus a catalogue, not a change to the pipeline,
 * the engine, or any effect written against them.</p>
 */
public final class CombatSheets {
    private CombatSheets() {}

    /** Vanilla's own baseline attack speed; dividing by it turns the attribute into our multiplier. */
    private static final double VANILLA_ATTACK_SPEED = 4.0;

    /** A walking player's movement speed, so {@code velocidad} reads as "times a player on foot". */
    private static final double VANILLA_WALK_SPEED = 0.1;

    /** Half-hearts per container. */
    private static final double HEALTH_PER_CONTAINER = 2.0;

    /**
     * The sheet an entity fights with right now.
     *
     * <p>Built fresh on every call rather than cached: {@code StatBlock} is explicitly a function of
     * its sources ({@link StatBlock}'s own contract), and a cache here would be the first place a
     * swapped weapon or a spent reliquia stopped being visible.</p>
     */
    public static StatBlock of(LivingEntity entity) {
        StatBlock sheet = new StatBlock();
        sheet.setBase(Stat.DANO, attribute(entity, Attributes.ATTACK_DAMAGE));
        sheet.setBase(Stat.ARMADURA, attribute(entity, Attributes.ARMOR));
        sheet.setBase(Stat.CADENCIA,
                Math.max(0.1, attribute(entity, Attributes.ATTACK_SPEED) / VANILLA_ATTACK_SPEED));
        sheet.setBase(Stat.VELOCIDAD, baseSpeed(entity) / VANILLA_WALK_SPEED);
        sheet.setBase(Stat.CONTENEDORES, entity.getMaxHealth() / HEALTH_PER_CONTAINER);
        // Armour toughness is vanilla's answer to big single hits, which the mitigation curve already
        // handles by shape. It maps to penetration RESISTANCE, which does not exist as a stat, so it
        // is deliberately dropped rather than folded somewhere it would double-count.
        applyWornGear(sheet, entity);
        return sheet;
    }

    /**
     * Adds the first-party lines of everything the entity is wearing.
     *
     * <p>Each piece is asked for its lines only <b>from the slot it counts in</b>
     * ({@link GearVanilla#slot}), which is the rule its vanilla lines already obey — otherwise a sword
     * parked in the off hand would hand over its crit and a full hotbar would be a stat stick. Charms
     * are the exception and apply from any curio slot, because a curio slot <i>is</i> where a charm
     * counts.</p>
     */
    private static void applyWornGear(StatBlock sheet, LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            GearDef def = GearHolder.defOf(stack);
            if (def != null && GearVanilla.slot(def.kind()).test(slot)) {
                GearSheet.apply(sheet, def.stats());
            }
        }
        for (ItemStack stack : GearCurios.wornCurios(entity)) {
            GearDef def = GearHolder.defOf(stack);
            if (def != null) {
                GearSheet.apply(sheet, def.stats());
            }
        }
    }

    /**
     * Poise: what a guard absorbs before it breaks.
     *
     * <p>An authored {@link Stat#APLOMO} wins, which is what makes that stat real — a piece of equipo,
     * or later a bestiary sheet, saying "hard to stagger" is now the answer, rather than a number
     * nothing in the game could reach. Absent one, the fallback below stands in.</p>
     *
     * <p>The fallback is deliberately flatter than a health bar: with flat chips
     * ({@link SwingState#poiseChip}) a full light chain is worth 5 and a heavy 8, so these numbers are
     * chosen against <i>those</i> rather than against health. Chaff lands near 8 — a heavy and a poke —
     * a brute near 18, and a boss near 33, which is four committed heavies and is meant to feel like a
     * plan rather than an accident. The previous {@code maxHealth / 2} broke everything in three
     * swings, which is how the stagger stopped being an event.</p>
     */
    public static double aplomoFor(LivingEntity entity) {
        double authored = of(entity).get(Stat.APLOMO);
        return authored > 0 ? authored : 3 + entity.getMaxHealth() / 4;
    }

    /**
     * The entity's base movement speed, without modifiers from sprinting, sneaking, or effects.
     *
     * <p>{@code getAttributeValue} returns the fully computed value — walk speed, sprint boost,
     * slowness potions, everything — so it changes from tick to tick. A stat that drifts while the
     * player is standing still is not a sheet: it is a speedometer. The base value is the multiplier
     * the entity was authored with, and is what the panel should show.</p>
     */
    private static double baseSpeed(LivingEntity entity) {
        return entity.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED)
                ? entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()
                : 0;
    }

    private static double attribute(LivingEntity entity,
                                    net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> which) {
        return entity.getAttributes().hasAttribute(which) ? entity.getAttributeValue(which) : 0;
    }
}
