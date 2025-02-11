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
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.game.dungeons.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchematicService {
    private final int roomSize = DungeonConfig.Dimensions.getRoomSize();
    private final int roomHeight = DungeonConfig.Dimensions.getRoomHeight();
    private final int doorSize = DungeonConfig.Dimensions.getDoorSize();

    private final Map<String, Clipboard> schematicCache = new HashMap<>();

    public void placeRoom(World world, BlockPos pos, Room room, Room[][] dungeon) {
        if(room.getType() == RoomType.WALL) return;
        if(room.getParentX() != null && room.getParentY() != null) {
            Room parent = dungeon[room.getParentY()][room.getParentX()];
            if(room.getShape().equals(RoomShape.L_SHAPE_BOTTOM_RIGHT) && room.getParentX() == room.getX() - 1 && room.getParentY() == room.getY()) {
                Teras.getLogger().info("Building L shaped room from parent " + parent.getType() + " at " + parent.getX() + ", " + parent.getY());
                room.setX(parent.getX());
                room.setY(parent.getY());
            } else {
                Teras.getLogger().info("Skipping child room " + room.getType() + " at " + room.getX() + ", " + room.getY());
                return;
            }
        }

        try {
            String schematicName = getSchematicNameForRoom(room);
            Clipboard clipboard = getOrLoadSchematic(schematicName);
            if (clipboard == null) {
                throw new IllegalStateException("Failed to load schematic: " + schematicName);
            }

            pasteSchematic(world, pos, room, dungeon, clipboard);
            placeDoors(world, pos, room, dungeon);
        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }

    private String getSchematicNameForRoom(Room room) {
        // Base schematic names for each type
        if (room.getShape() == RoomShape.L_SHAPE_TOP_LEFT ||
                room.getShape() == RoomShape.L_SHAPE_TOP_RIGHT ||
                room.getShape() == RoomShape.L_SHAPE_BOTTOM_LEFT ||
                room.getShape() == RoomShape.L_SHAPE_BOTTOM_RIGHT) {
            return "rooms/L";
        }

        if (room.getShape() == RoomShape.HORIZONTAL ||
                room.getShape() == RoomShape.VERTICAL) {
            return "rooms/long";
        }

        if (room.getShape() == RoomShape.QUAD) {
            return "rooms/large";
        }

        // Standard room types
        switch (room.getType()) {
            case START: return "rooms/starting";
            case BOSS:
            case MINI_BOSS: return "rooms/mini_boss";
            case SHOP: return "rooms/shop";
            case TREASURE: return "rooms/treasure";
            case SECRET:
            case SUPER_SECRET: return "rooms/secret";
            default: return "rooms/normal";
        }
    }

    private double getRotationForRoom(Room room, Room[][] dungeon) {
        if (room.getShape() == RoomShape.HORIZONTAL || room.getShape() == RoomShape.VERTICAL) {
            Direction childDirection = findChildDirection(room, dungeon);
            if (childDirection != null) {
                switch (childDirection) {
                    case SOUTH: return 0.0;
                    case WEST: return 90.0;
                    case NORTH: return 180.0;
                    case EAST: return 270.0;
                    default: return 0.0;
                }
            }
        }

        if (room.getShape().isLShaped()) {
            switch (room.getShape()) {
                case L_SHAPE_BOTTOM_LEFT: return 0.0;    // Base orientation
                case L_SHAPE_TOP_LEFT: return 90.0;      // Rotate 90 degrees clockwise
                case L_SHAPE_TOP_RIGHT: return 180.0;    // Rotate 180 degrees
                case L_SHAPE_BOTTOM_RIGHT: return 270.0; // Rotate 270 degrees clockwise
                default: return 0.0;
            }
        }

        return 0.0;
    }

    private Direction findChildDirection(Room room, Room[][] dungeon) {
        if (room.getShape() != RoomShape.HORIZONTAL && room.getShape() != RoomShape.VERTICAL) {
            return null;
        }

        // Check all adjacent positions
        int[][] directions = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}}; // SOUTH, WEST, NORTH, EAST
        Direction[] mappedDirections = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

        for (int i = 0; i < directions.length; i++) {
            int newX = room.getX() + directions[i][0];
            int newY = room.getY() + directions[i][1];

            // Check if position is within bounds
            if (newX >= 0 && newX < dungeon[0].length && newY >= 0 && newY < dungeon.length) {
                Room adjacentRoom = dungeon[newY][newX];
                // Check if this room has our current room as its parent
                if (adjacentRoom != null &&
                        adjacentRoom.getParentX() != null &&
                        adjacentRoom.getParentY() != null &&
                        adjacentRoom.getParentX() == room.getX() &&
                        adjacentRoom.getParentY() == room.getY()) {
                    return mappedDirections[i];
                }
            }
        }

        return null;
    }

    private BlockPos adjustRoomPosition(BlockPos pos, Room room, Room[][] dungeon, Clipboard clipboard, double rotation) {
        // Create a copy of the position to prevent affecting placeDoors
        BlockPos adjustedPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());

        if (room.getShape() == RoomShape.HORIZONTAL || room.getShape() == RoomShape.VERTICAL) {
            Direction childDirection = findChildDirection(room, dungeon);
            if (childDirection != null) {
                switch (childDirection) {
                    case SOUTH: // No offset needed
                        break;
                    case WEST: // No offset needed
                        break;
                    case NORTH:
                        adjustedPos = adjustedPos.offset(DungeonConfig.Dimensions.getRoomSize() - 1, 0, 0);
                        break;
                    case EAST:
                        adjustedPos = adjustedPos.offset(0, 0, DungeonConfig.Dimensions.getRoomSize() - 1);
                        break;
                }
            }
        } else if (room.getShape().isLShaped()) {
            int doubleRoomSize = DungeonConfig.Dimensions.getRoomSize() * 2 - 1;

            if (rotation == 90.0) {
                // Move 2 rooms - 1 block EAST
                adjustedPos = adjustedPos.offset(doubleRoomSize, 0, 0);
            } else if (rotation == 180.0) {
                // Move 2 rooms - 1 block EAST and 2 rooms - 1 block SOUTH
                adjustedPos = adjustedPos.offset(doubleRoomSize, 0, doubleRoomSize);
            } else if (rotation == 270.0) {
                // Move 1 room - 1 block SOUTH
                adjustedPos = adjustedPos.offset(0, 0, DungeonConfig.Dimensions.getRoomSize() - 1);
            }
            // Rotation 0: No offset needed
        }

        return adjustedPos;
    }

    private void pasteSchematic(World world, BlockPos pos, Room room, Room[][] dungeon, Clipboard clipboard)
            throws WorldEditException {
        // Calculate rotation based on room shape and position
        double rotation = getRotationForRoom(room, dungeon);

        // Use the new adjustRoomPosition function to get the correct position
        BlockPos pastePos = adjustRoomPosition(pos, room, dungeon, clipboard, rotation);

        BlockVector3 wePos = BlockVector3.at(pastePos.getX(), pastePos.getY(), pastePos.getZ());
        com.sk89q.worldedit.world.World weWorld = ForgeAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);

            if (rotation != 0.0) {
                AffineTransform transform = new AffineTransform();
                transform = transform.rotateY(-rotation); // Negative because WE uses counterclockwise rotation
                holder.setTransform(holder.getTransform().combine(transform));
            }

            Operation operation = holder
                    .createPaste(editSession)
                    .to(wePos)
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            editSession.flushSession();
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

    private void placeDoors(World world, BlockPos pos, Room room, Room[][] dungeon) {
        if (room.getType() == RoomType.WALL) return;

        // Get all valid edges of the room based on its shape
        List<Direction> directions = getValidDoorDirections(room);

        for (Direction direction : directions) {
            if (direction.getAxis().isVertical()) continue;

            int adjacentX = room.getX() + direction.getStepX();
            int adjacentY = room.getY() + direction.getStepZ();

            if (isValidAdjacentRoom(dungeon, adjacentX, adjacentY)) {
                BlockPos doorPos = calculateDoorPosition(pos, direction, room);
                createDoor(world, doorPos, direction, dungeon[adjacentY][adjacentX].getType());
            }
        }
    }

    private List<Direction> getValidDoorDirections(Room room) {
        List<Direction> directions = new ArrayList<>();

        if (room.getShape().isLShaped()) {
            // Add directions based on L-shape orientation
            switch (room.getShape()) {
                case L_SHAPE_TOP_LEFT:
                    directions.add(Direction.NORTH);
                    directions.add(Direction.WEST);
                    directions.add(Direction.SOUTH);
                    directions.add(Direction.EAST);
                    break;
                case L_SHAPE_TOP_RIGHT:
                    directions.add(Direction.NORTH);
                    directions.add(Direction.EAST);
                    directions.add(Direction.SOUTH);
                    directions.add(Direction.WEST);
                    break;
                case L_SHAPE_BOTTOM_LEFT:
                    directions.add(Direction.SOUTH);
                    directions.add(Direction.WEST);
                    directions.add(Direction.NORTH);
                    directions.add(Direction.EAST);
                    break;
                case L_SHAPE_BOTTOM_RIGHT:
                    directions.add(Direction.SOUTH);
                    directions.add(Direction.EAST);
                    directions.add(Direction.NORTH);
                    directions.add(Direction.WEST);
                    break;
            }
        } else {
            // For rectangular rooms, add all four directions
            directions.add(Direction.NORTH);
            directions.add(Direction.SOUTH);
            directions.add(Direction.EAST);
            directions.add(Direction.WEST);
        }

        return directions;
    }

    private BlockPos calculateDoorPosition(BlockPos pos, Direction direction, Room room) {
        int roomYOffset = doorSize - roomHeight;
        int roomOffsetOpp = (room.getShape() == RoomShape.HORIZONTAL ||
                room.getShape() == RoomShape.VERTICAL) ?
                roomSize * 2 - 1 : roomSize - 1;
        int roomOffset = (roomSize - doorSize) / 2;

        switch (direction) {
            case NORTH: return pos.offset(roomOffset, roomYOffset, 0);
            case EAST: return pos.offset(roomOffsetOpp, roomYOffset, roomOffset);
            case SOUTH: return pos.offset(roomOffset, roomYOffset, roomOffsetOpp);
            case WEST: return pos.offset(0, roomYOffset, roomOffset);
            default: return pos;
        }
    }

    private boolean isValidAdjacentRoom(Room[][] dungeon, int x, int y) {
        return x >= 0 && x < dungeon[0].length &&
                y >= 0 && y < dungeon.length &&
                dungeon[y][x].getType() != RoomType.WALL;
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
}