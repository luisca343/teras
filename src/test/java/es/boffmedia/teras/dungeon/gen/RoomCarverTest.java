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

    /** Every shape: these predate the piso opt-out and must keep their old behaviour. */
    private static final java.util.Set<RoomShape> ALL_SHAPES =
            java.util.EnumSet.allOf(RoomShape.class);


    private static GenConfig withGridSize(int gridSize) {
        GenConfig d = GenConfig.defaults();
        return new GenConfig(gridSize,
                d.chanceQuad(), d.chanceHorizontal(), d.chanceVertical(), d.chanceLShape(),
                d.largeShapeDecay(), d.shapeResetInterval(),
                d.curseRoomChance(), d.challengeRoomChance(),
                d.sacrificeRoomChance(), d.arcadeRoomChance(), d.devilDealChance(),
                d.miniBossChance(), d.firstStageMiniBossBoost(),
                d.labyrinthMultiplier(), d.labyrinthCellCap(), d.lostRoomBonus(),
                d.celdas(), d.jitter(), d.maxAttempts(), d.exitRoom(),
                d.postMargin(), d.forceBossQuad());
    }

    /**
     * The legacy carver read cells before checking bounds, so large-shape rolls at the grid edge
     * crashed generation. Saturating a tiny grid forces every roll to happen at the edge.
     */
    @Test
    void saturatingATinyGridNeverReadsOutOfBounds() {
        GenConfig config = withGridSize(5);
        for (long seed = 0; seed < 300; seed++) {
            RoomGrid grid = RoomCarver.carve(config, 200, 3, ALL_SHAPES, new SeededRng(seed));
            assertTrue(grid.occupiedCellCount() <= 25);
        }
    }

    @Test
    void overfullTargetStopsGracefullyWhenTheGridIsFull() {
        GenConfig config = withGridSize(7);
        RoomGrid grid = RoomCarver.carve(config, 500, 3, ALL_SHAPES, new SeededRng(1));
        assertTrue(grid.occupiedCellCount() <= 49);
        assertTrue(grid.rooms().size() > 1);
    }

    @Test
    void carvedFloorsMeetTheirDeadEndMinimum() {
        GenConfig config = GenConfig.defaults();
        for (long seed = 0; seed < 100; seed++) {
            RoomGrid grid = RoomCarver.carve(config, 22, 6, ALL_SHAPES, new SeededRng(seed));
            assertTrue(grid.deadEndCells().size() >= 6,
                    "seed " + seed + " has " + grid.deadEndCells().size() + " dead ends");
        }
    }

    /**
     * The top-up buys dead ends, not cells. Building onto a cell whose one neighbour is itself a
     * dead end trades one for another and leaves the floor a cell bigger for nothing, and picking
     * uniformly meant about half of all additions were that trade — floor one came out at 27.6 cells
     * against a budget of 10, which is what made the authored {@code celdas} curve nearly inert.
     *
     * <p>Held as a ratio rather than an absolute so it survives retuning: reaching the minimum must
     * not cost more than the floor was budgeted in the first place.</p>
     */
    @Test
    void theDeadEndTopUpDoesNotDoubleTheFloor() {
        GenConfig config = GenConfig.defaults();
        int carved = 0;
        int seeds = 200;
        for (long seed = 0; seed < seeds; seed++) {
            carved += RoomCarver.carve(config, 10, 6, ALL_SHAPES, new SeededRng(seed))
                    .occupiedCellCount();
        }
        double average = carved / (double) seeds;
        assertTrue(average < 20, "a 10-cell floor with six dead ends averages " + average
                + " cells; the top-up is paying for exchanges again");
    }

    /**
     * A piso that forces the 2×2 boss gets a carve that ends in a dead end able to hold it, so the
     * reroll loop does not have to go looking for one. Both halves matter: the deepest dead end must
     * be alone at its distance (ties are broken by grid order, so a tie could still hand the boss the
     * one that cannot grow) and it must have the 2×2 around it that
     * {@code SpecialRoomPlacer.isGrowable} will ask for.
     */
    @Test
    void forcingTheQuadMakesTheCarveEndInAGrowableDeadEnd() {
        GenConfig config = GenConfig.defaults().withForceBossQuad(true);
        int good = 0;
        int seeds = 200;
        for (long seed = 0; seed < seeds; seed++) {
            RoomGrid grid = RoomCarver.carve(config, 10, 7, ALL_SHAPES, new SeededRng(seed));
            java.util.Map<es.boffmedia.teras.dungeon.model.GridPos, Integer> distances =
                    grid.distancesFromCenter();
            java.util.List<es.boffmedia.teras.dungeon.model.GridPos> deepest =
                    new java.util.ArrayList<>();
            int farthest = 0;
            for (es.boffmedia.teras.dungeon.model.GridPos cell : grid.deadEndCells()) {
                farthest = Math.max(farthest, distances.getOrDefault(cell, 0));
            }
            for (es.boffmedia.teras.dungeon.model.GridPos cell : grid.deadEndCells()) {
                if (distances.getOrDefault(cell, 0) == farthest) {
                    deepest.add(cell);
                }
            }
            if (deepest.size() != 1) {
                continue;
            }
            // What SpecialRoomPlacer does with it: the farthest dead end becomes the boss, and the
            // boss is then grown. Asked of the placer's own code so the promise and the check cannot
            // drift apart.
            es.boffmedia.teras.dungeon.model.Room boss = grid.roomAt(deepest.get(0));
            boss.setType(es.boffmedia.teras.dungeon.model.RoomType.BOSS);
            SpecialRoomPlacer.growBossRoom(grid, new SeededRng(seed));
            if (grid.roomAt(deepest.get(0)).shape() == RoomShape.QUAD) {
                good++;
            }
        }
        assertTrue(good >= seeds * 95 / 100,
                "only " + good + " of " + seeds + " carves ended in a growable lone dead end");
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
