package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.forge.ForgeAdapter;
import es.boffmedia.teras.util.game.dungeons.DungeonGenerator;
import es.boffmedia.teras.util.game.dungeons.Room;
import es.boffmedia.teras.util.game.dungeons.RoomType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
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
                        context.getSource().getServer().execute(() -> placeRoomSchematic(context.getSource().getLevel(), pos, room.getType()));
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

    private void placeRoomSchematic(World world, BlockPos pos, RoomType type) {
        if(type == RoomType.WALL) return;
        String schematicName = getSchematicNameForRoomType(type);
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
        } catch (IOException | WorldEditException e) {
            e.printStackTrace();
        }
    }

    private String getSchematicNameForRoomType(RoomType type) {
        switch (type) {
            case WALL: return "dungeon_wall";
            case NORMAL: return "dungeon_room";
            case START: return "dungeon_room";
            case BOSS: return "dungeon_room";
            case SUPER_SECRET: return "dungeon_room";
            case SHOP: return "dungeon_room";
            case TREASURE: return "dungeon_room";
            case SECRET: return "dungeon_room";
            case CHALLENGE: return "dungeon_room";
            case CURSE: return "dungeon_room";
            case MINI_BOSS: return "dungeon_room";
            default: return "dungeon_room";
        }
    }
}