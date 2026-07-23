package es.boffmedia.teras.dungeon.gear;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gear catalog, held to the same standard the room audit holds a room to: nothing authored,
 * plausible and inert. Loot rolls a rarity and {@link GearRoll} picks any piece of it, so a piece is
 * reachable the moment it is in the catalog — which is exactly why an unreachable piece is a bug
 * worth a test rather than a shrug.
 */
class GearCatalogAuditTest {

    /**
     * Every ability with a handler, grouped by where it lives. {@link GearAbility} calls itself "the
     * registry of implemented behaviour", so a new value must land here — and next to its handler —
     * or it ships inert. This list is the forcing function that makes the two happen together.
     */
    private static final Set<GearAbility> HANDLED = EnumSet.of(
            // GearEvents.onMelee
            GearAbility.VAMPIRISMO, GearAbility.QUEMAZON, GearAbility.DESGARRO,
            GearAbility.VISCOSO, GearAbility.EMPUJE,
            // GearEvents.onDamaged / on kill / on death
            GearAbility.ESPINAS, GearAbility.ONDA, GearAbility.FENIX_MENOR,
            // GearEvents passives
            GearAbility.BOTIN, GearAbility.LINTERNA, GearAbility.CAIDA_SUAVE,
            // GadgetItem
            GearAbility.BENGALA, GearAbility.PETARDO, GearAbility.FRASCO, GearAbility.GARFIO);

    @Test
    void everyAbilityIsHandled() {
        List<String> inert = new ArrayList<>();
        for (GearAbility ability : GearAbility.values()) {
            if (ability != GearAbility.NINGUNA && !HANDLED.contains(ability)) {
                inert.add(ability.name() + " — add a handler and list it in HANDLED, or it does nothing");
            }
        }
        assertTrue(inert.isEmpty(), String.join("\n", inert));
    }

    @Test
    void everyRarityHasAPieceToDraw() {
        for (GearDef.Rarity rarity : GearDef.Rarity.values()) {
            boolean any = GearDefs.all().values().stream().anyMatch(d -> d.rarity() == rarity);
            assertTrue(any, "no piece of rarity " + rarity + " — a loot roll of it would draw nothing");
        }
    }

    /**
     * An active ability fires from a gadget's right-click and from nowhere else, so it is inert on
     * anything that is not a gadget, and a gadget with no active ability is a right-click that does
     * nothing. {@link GearAbility#isActive} exists to let this test insist the two agree.
     */
    @Test
    void activeAbilitiesAndGadgetsAgree() {
        List<String> problems = new ArrayList<>();
        for (GearDef def : GearDefs.all().values()) {
            boolean gadget = def.kind() == GearKind.GADGET;
            boolean fires = def.abilities().stream().anyMatch(a -> a.ability().isActive());
            for (AbilityDef ability : def.abilities()) {
                if (ability.ability().isActive() && !gadget) {
                    problems.add(def.id() + " carries active " + ability.ability()
                            + " but is a " + def.kind() + " — it can never be triggered");
                }
            }
            if (gadget && !fires) {
                problems.add(def.id() + " is a gadget with no active ability — its use does nothing");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyGearLootTableRollsAReachableRarity() {
        List<String> problems = new ArrayList<>();
        for (String table : List.of("boss", "treasure", "devil")) {
            List<JsonObject> rolls = new ArrayList<>();
            collectGearFunctions(loadTable(table), rolls);
            if (rolls.isEmpty()) {
                problems.add(table + ".json: names no teras:gear_aleatorio roll");
                continue;
            }
            for (JsonObject roll : rolls) {
                int total = weight(roll, "comun") + weight(roll, "raro") + weight(roll, "epico");
                if (total <= 0) {
                    problems.add(table + ".json: a gear roll has all-zero rarity weights — it draws nothing");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static int weight(JsonObject roll, String key) {
        return roll.has(key) ? roll.get(key).getAsInt() : 0;
    }

    private static void collectGearFunctions(JsonElement element, List<JsonObject> out) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("function")
                    && "teras:gear_aleatorio".equals(object.get("function").getAsString())) {
                out.add(object);
            }
            for (String key : object.keySet()) {
                collectGearFunctions(object.get(key), out);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : (JsonArray) element) {
                collectGearFunctions(child, out);
            }
        }
    }

    private static JsonElement loadTable(String name) {
        String path = "data/teras/loot_table/dungeon/" + name + ".json";
        try (InputStream in = GearCatalogAuditTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(in != null, "missing loot table " + path);
            return JsonParser.parseReader(new java.io.InputStreamReader(in));
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }
}
