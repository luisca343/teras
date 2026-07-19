package es.boffmedia.teras.net;

import es.boffmedia.teras.Teras;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the receiving player's dungeon minimap — discovered rooms with their state,
 * dim outlines for known-but-unentered neighbors, and the player's current cell. A full snapshot
 * every time, like {@link RaceHudPayload}: a 13×13 floor is a few hundred bytes and a snapshot
 * can never render a half-updated map.
 *
 * <p>The client is told, never asked: secret rooms and unexplored areas simply are not in the
 * payload until the server says so, so a modified client cannot reveal the floor. Under the
 * Curse of the Lost, {@code mapHidden} replaces the whole map.</p>
 *
 * @param active     false hides and clears the minimap (run ended / left the dungeon)
 * @param gridSize   floor grid width in cells
 * @param stage      current stage, shown on the map header
 * @param mapHidden  Curse of the Lost: show the curse notice instead of the map
 * @param currentX   the receiving player's grid cell, x
 * @param currentY   the receiving player's grid cell, y
 * @param cells      one entry per visible cell
 */
public record DungeonMapPayload(boolean active, int gridSize, int stage, boolean mapHidden,
                                int currentX, int currentY,
                                List<Cell> cells) implements CustomPacketPayload {

    /**
     * @param type  {@code RoomType} ordinal, or {@link #TYPE_UNKNOWN} for an adjacent room the
     *              player has seen a doorway to but never entered
     * @param state {@code RoomState} ordinal
     * @param label whether this cell carries the room's glyph. A multi-cell room sends several
     *              cells and only one of them is labelled — without it a 2×2 boss chamber drew
     *              four B's, which read as four boss rooms
     */
    public record Cell(int x, int y, int type, int state, boolean label) {}

    public static final int TYPE_UNKNOWN = -1;

    public static final Type<DungeonMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Teras.MOD_ID, "dungeon_map"));

    public static DungeonMapPayload hidden() {
        return new DungeonMapPayload(false, 0, 0, false, 0, 0, List.of());
    }

    public static final StreamCodec<FriendlyByteBuf, DungeonMapPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBoolean(payload.active());
                        buffer.writeVarInt(payload.gridSize());
                        buffer.writeVarInt(payload.stage());
                        buffer.writeBoolean(payload.mapHidden());
                        buffer.writeVarInt(payload.currentX());
                        buffer.writeVarInt(payload.currentY());
                        buffer.writeVarInt(payload.cells().size());
                        for (Cell cell : payload.cells()) {
                            buffer.writeVarInt(cell.x());
                            buffer.writeVarInt(cell.y());
                            buffer.writeVarInt(cell.type());
                            buffer.writeVarInt(cell.state());
                            buffer.writeBoolean(cell.label());
                        }
                    },
                    buffer -> {
                        boolean active = buffer.readBoolean();
                        int gridSize = buffer.readVarInt();
                        int stage = buffer.readVarInt();
                        boolean mapHidden = buffer.readBoolean();
                        int currentX = buffer.readVarInt();
                        int currentY = buffer.readVarInt();
                        int count = buffer.readVarInt();
                        List<Cell> cells = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            cells.add(new Cell(buffer.readVarInt(), buffer.readVarInt(),
                                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
                        }
                        return new DungeonMapPayload(active, gridSize, stage, mapHidden,
                                currentX, currentY, cells);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
