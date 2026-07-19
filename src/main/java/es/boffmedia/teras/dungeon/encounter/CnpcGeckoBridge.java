package es.boffmedia.teras.dungeon.encounter;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Gives an installed CustomNPCs enemy a GeckoLib model, through the
 * <a href="https://www.curseforge.com/minecraft/mc-mods/cnpc-gecko-addon">CNPC Gecko Addon</a>
 * (NeoForge 1.21.1, {@code com.goodbird.cnpcgeckoaddon}). The addon mixes a {@code CustomModelData}
 * onto CustomNPCs' display object, so the model rides the NPC's own saved and synced data and its
 * renderer does the drawing — exactly the integration the dungeon plan wanted and assumed did not
 * exist on 1.21.1.
 *
 * <p><b>Reflection on purpose.</b> The addon publishes no maven artifact, and it is optional: a
 * server without it must still install the bestiary, just with plain skins. Reflection keeps it out
 * of the build entirely and turns "not installed" into a no-op instead of a missing class. The
 * shape it needs is small and stable — {@code getCustomModelData()} on the display, then setters
 * on the returned object.</p>
 *
 * <p>Models are resolved through GeckoLib's cache by resource location, so Teras' own shipped
 * {@code assets/teras/geo} and {@code assets/teras/animations} files are valid targets, and the
 * animation names it expects ({@code idle} / {@code walk} / {@code attack}) are the ones they
 * already define.</p>
 */
public final class CnpcGeckoBridge {
    private CnpcGeckoBridge() {}

    public static final String MOD_ID = "cnpcgeckoaddon";

    public static boolean available() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Applies {@code model} to {@code display} (a CustomNPCs {@code INPCDisplay}). Silently does
     * nothing when the addon is absent or its shape has moved — a missing model is a cosmetic
     * loss, never a failed install.
     *
     * @param display the NPC's display object, straight from the CustomNPCs API
     * @param model   geo model resource location, e.g. {@code teras:geo/dungeon_guardian.geo.json}
     * @param animFile animation resource location
     * @param width   hitbox width the addon should use, in blocks
     * @param height  hitbox height the addon should use, in blocks
     * @return whether the model was applied
     */
    public static boolean applyModel(Object display, String model, String animFile,
                                     float width, float height) {
        if (!available() || display == null) {
            return false;
        }
        try {
            Method getData = display.getClass().getMethod("getCustomModelData");
            Object data = getData.invoke(display);
            if (data == null) {
                return false;
            }
            set(data, "setModel", String.class, model);
            set(data, "setAnimFile", String.class, animFile);
            set(data, "setIdleAnim", String.class, "idle");
            set(data, "setWalkAnim", String.class, "walk");
            set(data, "setAttackAnim", String.class, "attack");
            set(data, "setHeadBoneName", String.class, "head");
            set(data, "setWidth", float.class, width);
            set(data, "setHeight", float.class, height);
            return true;
        } catch (Throwable t) {
            Teras.LOGGER.warn("Dungeons: CNPC Gecko addon present but its model data did not "
                    + "accept '{}' — falling back to the plain skin: {}", model, t.toString());
            return false;
        }
    }

    private static void set(Object target, String method, Class<?> type, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(method, type).invoke(target, value);
    }
}
