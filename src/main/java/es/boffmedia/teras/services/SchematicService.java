package es.boffmedia.teras.services;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.game.dungeons.DungeonConfig;
import es.boffmedia.teras.util.game.dungeons.Room;
import es.boffmedia.teras.util.game.dungeons.RoomType;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchematicService {
    private final int roomSize = DungeonConfig.Dimensions.getRoomSize();
    private final int roomHeight = DungeonConfig.Dimensions.getRoomHeight();
    private final int doorSize = DungeonConfig.Dimensions.getDoorSize();

    private final Map<String, Clipboard> schematicCache = new HashMap<>();

    public void placeRoom(World world, BlockPos pos, Room room, Room[][] dungeon) {
        String schematicName = getSchematicNameForRoomType(room);

        try {
            Clipboard clipboard = getOrLoadSchematic(schematicName);
            if (clipboard == null) {
                throw new IllegalStateException("Failed to load schematic: " + schematicName);
            }

            pasteSchematic(world, pos, clipboard);
            placeDoors(world, pos, room, dungeon);
        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }

    private Clipboard getOrLoadSchematic(String schematicName) throws IOException {
        if (schematicCache.containsKey(schematicName)) {
            return schematicCache.get(schematicName);
        }

        File schem = FileHelper.getSchematic(schematicName);
        if (!schem.exists() || schem.length() == 0) {
            throw new IOException("Schematic file " + schematicName + " is missing or empty.");
        }

        try (FileInputStream fis = new FileInputStream(schem)) {
            ClipboardReader reader = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(fis);
            Clipboard clipboard = reader.read();
            schematicCache.put(schematicName, clipboard);
            return clipboard;
        }
    }

    private void pasteSchematic(World world, BlockPos pos, Clipboard clipboard) throws WorldEditException {
        BlockVector3 pastePos = BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
        com.sk89q.worldedit.world.World weWorld = ForgeAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pastePos)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            editSession.flushSession();
        }
    }

    private void placeDoors(World world, BlockPos pos, Room room, Room[][] dungeon) {
        if (room.getType() == RoomType.WALL) return;

        for (Direction direction : Direction.values()) {
            if (direction.getAxis().isVertical()) continue;

            int adjacentX = room.getX() + direction.getStepX();
            int adjacentY = room.getY() + direction.getStepZ();

            if (isValidAdjacentRoom(dungeon, adjacentX, adjacentY)) {
                BlockPos doorPos = calculateDoorPosition(pos, direction);
                createDoor(world, doorPos, direction, dungeon[adjacentY][adjacentX].getType());
            }
        }
    }

    private boolean isValidAdjacentRoom(Room[][] dungeon, int x, int y) {
        return x >= 0 && x < dungeon[0].length &&
                y >= 0 && y < dungeon.length &&
                dungeon[y][x].getType() != RoomType.WALL;
    }

    private BlockPos calculateDoorPosition(BlockPos pos, Direction direction) {
            int roomYOffset = doorSize - roomHeight;
        int roomOffsetOpp = roomSize - 1;
        int roomOffset = (roomSize - doorSize) / 2;

        switch (direction) {
            case NORTH: return pos.offset(roomOffset, roomYOffset, 0);
            case EAST: return pos.offset(roomOffsetOpp, roomYOffset, roomOffset);
            case SOUTH: return pos.offset(roomOffset, roomYOffset, roomOffsetOpp);
            case WEST: return pos.offset(0, roomYOffset, roomOffset);
            default: return pos;
        }
    }

    private void createDoor(World world, BlockPos pos, Direction direction, RoomType adjacentRoomType) {
        if (adjacentRoomType == RoomType.SECRET || adjacentRoomType == RoomType.SUPER_SECRET) {
            return;
        }

        boolean isHorizontal = direction.getAxis() == Direction.Axis.Z;
        for (int i = 0; i < doorSize; i++) {
            for (int h = 0; h < doorSize; h++) {
                BlockPos doorBlock = pos.offset(
                        isHorizontal ? i : 0,
                        h,
                        isHorizontal ? 0 : i
                );
                world.setBlock(doorBlock, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private String getSchematicNameForRoomType(Room room) {
        switch (room.getType()) {
            case START: return "rooms/starting";
            case BOSS:
            case MINI_BOSS: return "rooms/boss";
            case SHOP: return "rooms/shop";
            case TREASURE: return "rooms/treasure";
            case SECRET:
            case SUPER_SECRET: return "rooms/secret";
            default: return "rooms/normal";
        }
    }
}