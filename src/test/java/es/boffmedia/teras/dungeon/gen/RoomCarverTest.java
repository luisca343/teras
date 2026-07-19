package es.boffmedia.teras.dungeon.gen;

import es.boffmedia.teras.dungeon.model.Curse;
import es.boffmedia.teras.dungeon.model.DungeonLayout;
import es.boffmedia.teras.dungeon.model.RoomGrid;
import es.boffmedia.teras.dungeon.model.RoomShape;
import es.boffmedia.teras.dungeon.model.SeededRng;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCarverTest {

    private static GenConfig withGridSize(int gridSize) {
        GenConfig d = GenConfig.defaults();
        return new GenConfig(gridSize,
                d.chanceQuad(), d.chanceHorizontal(), d.chanceVertical(), d.chanceLShape(),
                d.largeShapeDecay(), d.shapeResetInterval(),
                d.curseRoomChance(), d.challengeRoomChance(),
                d.sacrificeRoomChance(), d.arcadeRoomChance(), d.devilDealChance(),
                d.miniBossChance(), d.firstStageMiniBossBoost(),
                d.labyrinthMultiplier(), d.labyrinthRoomCap(), d.lostRoomBonus(),
                d.finalStage(), d.finalStageRooms(), d.maxAttempts());
    }

    /**
     * The legacy carver read cells before checking bounds, so large-shape rolls at the grid edge
     * crashed generation. Saturating a tiny grid forces every roll to happen at the edge.
     */
    @Test
    void saturatingATinyGridNeverReadsOutOfBounds() {
        GenConfig config = withGridSize(5);
        for (long seed = 0; seed < 300; seed++) {
            RoomGrid grid = RoomCarver.carve(config, 200, 3, new SeededRng(seed));
            assertTrue(grid.occupiedCellCount() <= 25);
        }
    }

    @Test
    void overfullTargetStopsGracefullyWhenTheGridIsFull() {
        GenConfig config = withGridSize(7);
        RoomGrid grid = RoomCarver.carve(config, 500, 3, new SeededRng(1));
        assertTrue(grid.occupiedCellCount() <= 49);
        assertTrue(grid.rooms().size() > 1);
    }

    @Test
    void carvedFloorsMeetTheirDeadEndMinimum() {
        GenConfig config = GenConfig.defaults();
        for (long seed = 0; seed < 100; seed++) {
            RoomGrid grid = RoomCarver.carve(config, 22, 6, new SeededRng(seed));
            assertTrue(grid.deadEndCells().size() >= 6,
                    "seed " + seed + " has " + grid.deadEndCells().size() + " dead ends");
        }
    }

    /** L_BOTTOM_RIGHT was disabled in the legacy carver; it must occur again. */
    @Test
    void allFourLOrientationsOccur() {
        Set<RoomShape> seen = new java.util.HashSet<>();
        for (int seed = 0; seed < 400 && seen.size() < 4; seed++) {
            DungeonLayout layout = DungeonGenerator.generate(
                    GenConfig.defaults(), 6, Set.of(Curse.LABYRINTH), "l-shapes-" + seed);
            layout.rooms().forEach(r -> {
                if (r.shape().isLShaped()) {
                    seen.add(r.shape());
                }
            });
        }
        assertTrue(seen.contains(RoomShape.L_BOTTOM_RIGHT), "saw only " + seen);
        assertTrue(seen.size() == 4, "saw only " + seen);
    }
}
