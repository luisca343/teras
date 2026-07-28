package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.dungeon.gear.GearDef;
import es.boffmedia.teras.dungeon.gear.GearDefs;
import es.boffmedia.teras.dungeon.gear.GearKind;
import es.boffmedia.teras.dungeon.gear.GearOp;
import es.boffmedia.teras.dungeon.gear.GearStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a piece of equipo's first-party lines reach the sheet. */
class GearSheetTest {

    @Test
    @DisplayName("every first-party gear stat maps to a sheet axis, and every vanilla one maps to none")
    void mappingIsCompleteAndDisjoint() {
        for (GearStat gear : GearStat.values()) {
            Stat mapped = GearSheet.statOf(gear);
            if (gear.vanilla()) {
                assertNull(mapped, gear + " is vanilla-backed and must not also reach the sheet");
            } else {
                assertNotNull(mapped, gear + " is first-party and nothing reads it");
                assertEquals(gear.key(), mapped.key(),
                        "a gear key and its sheet axis must be the same word");
            }
        }
    }

    @Test
    @DisplayName("a flat line adds and a fraction line scales")
    void flatAddsAndFractionScales() {
        StatBlock sheet = new StatBlock();
        GearSheet.apply(sheet, List.of(
                new GearDef.Stat(GearStat.CRITICO, 0.10, GearOp.FLAT),
                new GearDef.Stat(GearStat.ENFRIAMIENTO, 0.5, GearOp.FRACTION_OF_BASE)));

        assertEquals(0.10, sheet.get(Stat.CRITICO), 1e-9);
        // enfriamiento's base is 1, so +50% is 1.5 rather than 0.5.
        assertEquals(1.5, sheet.get(Stat.ENFRIAMIENTO), 1e-9);
    }

    @Test
    @DisplayName("two pieces saying the same thing stack additively, not multiplicatively")
    void twoPiecesStackAdditively() {
        StatBlock sheet = new StatBlock();
        List<GearDef.Stat> half = List.of(
                new GearDef.Stat(GearStat.ENFRIAMIENTO, 0.5, GearOp.FRACTION_OF_BASE));
        GearSheet.apply(sheet, half);
        GearSheet.apply(sheet, half);

        assertEquals(2.0, sheet.get(Stat.ENFRIAMIENTO), 1e-9,
                "two +50% pieces make x2.0; multiplicative stacking is what makes a late run illegible");
    }

    @Test
    @DisplayName("vanilla-backed lines are ignored here, so a weapon's damage is never counted twice")
    void vanillaLinesAreNotDoubleCounted() {
        StatBlock sheet = new StatBlock();
        GearSheet.apply(sheet, List.of(
                new GearDef.Stat(GearStat.ATTACK_DAMAGE, 9.0, GearOp.FLAT),
                new GearDef.Stat(GearStat.ARMOR, 6.0, GearOp.FLAT)));

        // Both already reached the entity as attribute modifiers; CombatSheets reads them from there.
        assertEquals(0, sheet.get(Stat.DANO), 1e-9);
        assertEquals(0, sheet.get(Stat.ARMADURA), 1e-9);
    }

    @Test
    @DisplayName("nulls and an empty list are survivable")
    void nullsAreSurvivable() {
        StatBlock sheet = new StatBlock();
        GearSheet.apply(sheet, null);
        GearSheet.apply(sheet, List.of());
        GearSheet.apply(null, List.of());
        assertEquals(0, sheet.get(Stat.CRITICO), 1e-9);
    }

    @Test
    @DisplayName("the shipped catalog actually reaches crit, pierce, reach, cooldown and poise")
    void shippedCatalogSourcesTheNewAxes() {
        // The point of the whole exercise: before anything authored these, critico sat at its base of
        // zero and no hit in the dungeon could crit. A catalog that names none of them would leave the
        // panel showing numbers that cannot move, which is what this pins against.
        Map<String, GearDef> catalog = GearDefs.defaults();
        for (GearStat wanted : List.of(GearStat.CRITICO, GearStat.CONTUNDENCIA, GearStat.PENETRACION,
                GearStat.ALCANCE, GearStat.ENFRIAMIENTO, GearStat.APLOMO)) {
            assertTrue(catalog.values().stream()
                            .flatMap(def -> def.stats().stream())
                            .anyMatch(line -> line.stat() == wanted),
                    "no shipped piece sources " + wanted.key() + ", so it can never move");
        }
    }

    @Test
    @DisplayName("a sword crits and an axe pierces — the kinds differ on an axis, not just on speed")
    void swordAndAxeDifferOnTheirOwnAxis() {
        Map<String, GearDef> catalog = GearDefs.defaults();
        StatBlock sword = new StatBlock();
        StatBlock axe = new StatBlock();
        GearSheet.apply(sword, catalog.get("espada_abisal").stats());
        GearSheet.apply(axe, catalog.get("cachiporra").stats());

        assertEquals(GearKind.SWORD, catalog.get("espada_abisal").kind());
        assertEquals(GearKind.AXE, catalog.get("cachiporra").kind());
        assertTrue(sword.get(Stat.CRITICO) > 0, "a sword rewards landing a lot of hits");
        assertEquals(0, sword.get(Stat.PENETRACION), 1e-9);
        assertTrue(axe.get(Stat.PENETRACION) > 0, "an axe goes through what a deep floor is wearing");
        assertEquals(0, axe.get(Stat.CRITICO), 1e-9);
    }
}
