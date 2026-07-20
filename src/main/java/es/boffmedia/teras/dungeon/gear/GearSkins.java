package es.boffmedia.teras.dungeon.gear;

import es.boffmedia.teras.Teras;
import moe.plushie.armourers_workshop.core.skin.SkinDescriptor;
import moe.plushie.armourers_workshop.core.skin.SkinType;
import moe.plushie.armourers_workshop.core.skin.SkinTypes;
import moe.plushie.armourers_workshop.init.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * The Armourer's Workshop half of dungeon gear: the look. Teras owns the stats and abilities; AW
 * owns how a piece renders, through the {@code armourers_workshop:skin} data component its own
 * skinning table writes — so a stamped stack is indistinguishable from a player-skinned one and
 * needs no renderer of ours.
 *
 * <p>The only file in the mod that imports {@code moe.plushie.*}, and every entry point is guarded
 * by {@link ModList}: AW is a {@code compileOnly} dependency, so a server without it must still run
 * the dungeon with plain item models rather than fail on a missing class.</p>
 */
public final class GearSkins {
    private GearSkins() {}

    public static final String MOD_ID = "armourers_workshop";

    private static Boolean available;

    public static boolean available() {
        if (available == null) {
            available = ModList.get().isLoaded(MOD_ID);
        }
        return available;
    }

    /**
     * The skin the stack actually carries, as AW will read it — null when it carries none (or AW
     * is absent). Diagnostic-only, for {@code /teras dungeon gear}.
     */
    public static String describe(ItemStack stack) {
        if (!available()) {
            return null;
        }
        try {
            SkinDescriptor descriptor = ModDataComponents.SKIN.get().get(stack);
            if (descriptor == null || descriptor.isEmpty()) {
                return null;
            }
            return descriptor.identifier() + " (tipo " + descriptor.type() + ")";
        } catch (Throwable e) {
            return "error: " + e;
        }
    }

    /**
     * Brings {@code stack}'s skin in line with {@code def}: attaches it, or strips one a previous
     * catalog put there — a skin retuned away in {@code gear.json} has to come off pieces already
     * carrying it. Silently does nothing when AW is absent; a piece without a skin still fights
     * exactly the same.
     */
    public static void apply(ItemStack stack, GearDef def) {
        if (!available()) {
            return;
        }
        try {
            if (!def.hasSkin()) {
                ModDataComponents.SKIN.get().remove(stack);
                return;
            }
            // byName prepends "armourers:" and matches the bare lowercase names SkinTypes
            // registers ("sword", "head", …) — GearDefs.canonicalSkinType feeds it those.
            SkinType type = SkinTypes.byName(def.effectiveSkinType());
            if (type == null || type == SkinTypes.UNKNOWN) {
                Teras.LOGGER.warn("Dungeons: gear '{}' names unknown skin type '{}'",
                        def.id(), def.effectiveSkinType());
                return;
            }
            ModDataComponents.SKIN.get().set(stack, new SkinDescriptor(def.skinId(), type));
        } catch (Throwable e) {
            // A skin that will not attach costs this piece its looks, never the drop.
            Teras.LOGGER.warn("Dungeons: could not skin gear '{}': {}", def.id(), e.toString());
        }
    }
}
