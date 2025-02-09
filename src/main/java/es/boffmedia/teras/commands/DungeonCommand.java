package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.forge.ForgeAdapter;
import es.boffmedia.teras.util.game.dungeons.DungeonGenerator;
import es.boffmedia.teras.util.game.dungeons.DungeonUtils;
import es.boffmedia.teras.util.game.dungeons.Room;
import es.boffmedia.teras.util.game.dungeons.RoomType;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import es.boffmedia.teras.util.file.FileHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class DungeonCommand {
    public DungeonCommand(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("generardungeon")
                .then(Commands.argument("stageId", IntegerArgumentType.integer(1, 12))
                        .then(Commands.argument("curseOfTheLabyrinth", BoolArgumentType.bool())
                                .then(Commands.argument("curseOfTheLost", BoolArgumentType.bool())
                                        .executes(this::generarDungeon)
                                        .then(Commands.argument("seed", StringArgumentType.string())
                                                .executes(this::generarDungeonConSeed)
                                        )
                                )
                        )
                )
        );
    }

    private int generarDungeon(CommandContext<CommandSource> context) throws CommandSyntaxException {
        return generarDungeonInternal(context, null);
    }

    private int generarDungeonConSeed(CommandContext<CommandSource> context) throws CommandSyntaxException {
        String seed = StringArgumentType.getString(context, "seed");
        return generarDungeonInternal(context, seed);
    }

    private int generarDungeonInternal(CommandContext<CommandSource> context, String seed) throws CommandSyntaxException {
        int stageId = IntegerArgumentType.getInteger(context, "stageId");
        boolean curseOfTheLabyrinth = BoolArgumentType.getBool(context, "curseOfTheLabyrinth");
        boolean curseOfTheLost = BoolArgumentType.getBool(context, "curseOfTheLost");

        new Thread(() -> {
            DungeonGenerator.DungeonResult result = DungeonGenerator.generateDungeon(stageId, curseOfTheLabyrinth, curseOfTheLost, seed);
            Room[][] dungeon = result.dungeon;

            try {
                // Place the dungeon blocks in the world
                BlockPos startPos = context.getSource().getPlayerOrException().blockPosition();
                for (int z = 0; z < dungeon.length; z++) {
                    for (int x = 0; x < dungeon[z].length; x++) {
                        Room room = dungeon[z][x];
                        BlockPos pos = startPos.offset(x * 21, 0, z * 21);
                        if(room.getType() == RoomType.WALL) continue;
                        context.getSource().getServer().execute(() -> placeRoomSchematic(context.getSource().getLevel(), pos, room, dungeon));
                        // Update progress
                        context.getSource().sendSuccess(new StringTextComponent("Generating dungeon: " + (z * dungeon.length + x + 1) + "/" + (dungeon.length * dungeon.length)), true);
                    }
                }

                // Teleport player to the center of the starting room
                Room startRoom = findStartRoom(dungeon);
                if (startRoom != null) {
                    // This teleports the player to the CENTER of the starting room
                    BlockPos startRoomPos = startPos.offset(startRoom.getX() * 21 + 10, 0, startRoom.getY() * 21 + 10);
                    context.getSource().getServer().execute(() -> {
                        try {
                            context.getSource().getPlayerOrException().teleportTo(startRoomPos.getX(), startRoomPos.getY(), startRoomPos.getZ());
                        } catch (CommandSyntaxException e) {
                            e.printStackTrace();
                        }
                    });
                }

                // Send final message with the dungeon layout
                StringBuilder dungeonLayout = new StringBuilder("Dungeon Layout:\n");
                for (Room[] row : dungeon) {
                    for (Room room : row) {
                        dungeonLayout.append(getRoomSymbol(room)).append(" ");
                    }
                    dungeonLayout.append("\n");
                }
                dungeonLayout.append("Seed: ").append(result.seed);
                context.getSource().sendSuccess(new StringTextComponent(dungeonLayout.toString()), true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return 1;
    }

    private Room findStartRoom(Room[][] dungeon) {
        for (Room[] row : dungeon) {
            for (Room room : row) {
                if (room.getType() == RoomType.START) {
                    return room;
                }
            }
        }
        return null;
    }

    private String getRoomSymbol(Room room) {
        switch (room.getType()) {
            case WALL: return "█";
            case NORMAL: return "□";
            case START: return "S";
            case BOSS: return "B";
            case SUPER_SECRET: return "X";
            case SHOP: return "$";
            case TREASURE: return "T";
            case SECRET: return "?";
            case CHALLENGE: return "C";
            case CURSE: return "!";
            case MINI_BOSS: return "M";
            default: return " ";
        }
    }

    private void placeRoomSchematic(World world, BlockPos pos, Room room, Room[][] dungeon) {
        String schematicName = getSchematicNameForRoomType(room);
        File schem = FileHelper.getSchematic(schematicName);
        if (!schem.exists() || schem.length() == 0) {
            System.err.println("Schematic file " + schematicName + " is missing or empty.");
            return;
        }
        try {
            ClipboardReader reader = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(new FileInputStream(schem));
            Clipboard clipboard = reader.read();

            BlockVector3 pastePos = BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
            com.sk89q.worldedit.world.World weWorld = ForgeAdapter.adapt(world);
            EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1);

            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pastePos)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            editSession.flushSession();

            // Place doors after placing the room schematic
            placeDoors(world, pos, room, dungeon);

        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }

    private void placeDoors(World world, BlockPos pos, Room room, Room[][] dungeon) {
        int roomX = room.getX();
        int roomY = room.getY();

        // Check and place doors for each direction
        placeDoorIfAdjacent(world, pos, roomX, roomY, roomX + 1, roomY, Direction.EAST, dungeon);
        placeDoorIfAdjacent(world, pos, roomX, roomY, roomX - 1, roomY, Direction.WEST, dungeon);
        placeDoorIfAdjacent(world, pos, roomX, roomY, roomX, roomY + 1, Direction.SOUTH, dungeon);
        placeDoorIfAdjacent(world, pos, roomX, roomY, roomX, roomY - 1, Direction.NORTH, dungeon);
    }

    private void placeDoorIfAdjacent(World world, BlockPos pos, int x1, int y1, int x2, int y2, Direction direction, Room[][] dungeon) {
        Room room = dungeon[y2][x2];
        if (DungeonUtils.isValidRoom(x2, y2) && room.getType() != RoomType.WALL) {
            BlockPos doorPos = getDoorPosition(pos, direction);
            if(room.getType() != RoomType.SECRET && room.getType() != RoomType.SUPER_SECRET) create3x3AirDoor(world, doorPos, direction);
        }
    }


    private int ROOM_SIZE = 21;
    private int ROOM_HEIGHT = 8;
    private int DOOR_SIZE = 3;

    // Gets the position of the bottom left corner of the door given the room position and direction
    private BlockPos getDoorPosition(BlockPos pos, Direction direction) {
        int ROOM_Y_OFFSET = DOOR_SIZE - ROOM_HEIGHT;

        int ROOM_OFFSET_OPP = ROOM_SIZE - 1;
        int ROOM_OFFSET = (ROOM_SIZE - DOOR_SIZE) / 2;

        switch (direction) {
            case NORTH:
                return pos.offset(ROOM_OFFSET, ROOM_Y_OFFSET, 0);
            case EAST:
                return pos.offset(ROOM_OFFSET_OPP, ROOM_Y_OFFSET, ROOM_OFFSET);
            case SOUTH:
                return pos.offset(ROOM_OFFSET, ROOM_Y_OFFSET, ROOM_OFFSET_OPP);
            case WEST:
                return pos.offset(0, ROOM_Y_OFFSET, ROOM_OFFSET);
            default:
                return pos;
        }
    }

    private void create3x3AirDoor(World world, BlockPos pos, Direction direction) {
        int dx = 0, dz = 0;
        switch (direction) {
            case EAST:
            case WEST:
                dz = 1;
                break;
            case NORTH:
            case SOUTH:
                dx = 1;
                break;
        }

        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                world.setBlock(pos.offset(dx * i, j, dz * i), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private String getSchematicNameForRoomType(Room room) {
        /*
        if(room.getWidth() == 2 && room.getHeight() == 2) {
            return "dungeon_room_2x2";
        }

        if(room.getWidth() + room.getHeight() == 3) {
            return "dungeon_room_1x2";
        }*/

        switch (room.getType()) {
            case NORMAL: return "rooms/normal";
            case START: return "rooms/starting";
            case BOSS: return "rooms/boss";
            case SUPER_SECRET: return "rooms/secret";
            case SHOP: return "rooms/shop";
            case TREASURE: return "rooms/treasure";
            case SECRET: return "rooms/secret";
            case CHALLENGE: return "rooms/normal";
            case CURSE: return "rooms/normal";
            case MINI_BOSS: return "rooms/boss";
            default: return "rooms/normal";
        }
    }
}