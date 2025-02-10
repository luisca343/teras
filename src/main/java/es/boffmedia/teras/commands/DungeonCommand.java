package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.services.SchematicService;
import es.boffmedia.teras.util.game.dungeons.DungeonGenerator;
import es.boffmedia.teras.util.game.dungeons.Room;
import es.boffmedia.teras.util.game.dungeons.RoomType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;

public class DungeonCommand {
    private final SchematicService schematicService;

    public DungeonCommand(CommandDispatcher<CommandSource> dispatcher) {
        this.schematicService = new SchematicService();
        registerCommand(dispatcher);
    }

    private void registerCommand(CommandDispatcher<CommandSource> dispatcher) {
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
            try {
                generateAndPlaceDungeon(context, stageId, curseOfTheLabyrinth, curseOfTheLost, seed);
            } catch (Exception e) {
                e.printStackTrace();
                context.getSource().sendFailure(new StringTextComponent("Error generating dungeon: " + e.getMessage()));
            }
        }).start();

        return 1;
    }

    private void generateAndPlaceDungeon(CommandContext<CommandSource> context, int stageId,
                                         boolean curseOfTheLabyrinth, boolean curseOfTheLost, String seed) throws CommandSyntaxException {

        DungeonGenerator.DungeonResult result = DungeonGenerator.generateDungeon(
                stageId, curseOfTheLabyrinth, curseOfTheLost, seed);
        Room[][] dungeon = result.dungeon;

        BlockPos startPos = context.getSource().getPlayerOrException().blockPosition();
        placeRooms(context, dungeon, startPos);
        teleportPlayerToStart(context, dungeon, startPos);
        displayDungeonLayout(context, dungeon, result.seed);
    }

    private void placeRooms(CommandContext<CommandSource> context, Room[][] dungeon, BlockPos startPos) {
        int totalRooms = dungeon.length * dungeon.length;
        int processedRooms = 0;

        for (int z = 0; z < dungeon.length; z++) {
            for (int x = 0; x < dungeon[z].length; x++) {
                Room room = dungeon[z][x];
                if (room.getType() != RoomType.WALL) {
                    final int currentRoom = ++processedRooms;
                    BlockPos pos = startPos.offset(x * 21, 0, z * 21);

                    context.getSource().getServer().execute(() -> {
                        schematicService.placeRoom(context.getSource().getLevel(), pos, room, dungeon);
                        context.getSource().sendSuccess(
                                new StringTextComponent(String.format("Generating dungeon: %d/%d", currentRoom, totalRooms)),
                                true
                        );
                    });
                }
            }
        }
    }

    private void teleportPlayerToStart(CommandContext<CommandSource> context, Room[][] dungeon, BlockPos startPos)
            throws CommandSyntaxException {
        Room startRoom = findStartRoom(dungeon);
        if (startRoom != null) {
            BlockPos startRoomPos = startPos.offset(startRoom.getX() * 21 + 10, 0, startRoom.getY() * 21 + 10);
            context.getSource().getServer().execute(() -> {
                try {
                    context.getSource().getPlayerOrException().teleportTo(
                            startRoomPos.getX(), startRoomPos.getY(), startRoomPos.getZ()
                    );
                } catch (CommandSyntaxException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private Room findStartRoom(Room[][] dungeon) {
        for (int y = 0; y < dungeon.length; y++) {
            for (int x = 0; x < dungeon[y].length; x++) {
                if (dungeon[y][x].getType() == RoomType.START) {
                    return dungeon[y][x];
                }
            }
        }
        return null;
    }

    private void displayDungeonLayout(CommandContext<CommandSource> context, Room[][] dungeon, String seed) {
        StringBuilder layout = new StringBuilder("Dungeon Layout:\n");
        for (Room[] row : dungeon) {
            for (Room room : row) {
                layout.append(getRoomSymbol(room.getType())).append(" ");
            }
            layout.append("\n");
        }
        layout.append("Seed: ").append(seed);

        context.getSource().sendSuccess(new StringTextComponent(layout.toString()), true);
    }

    private String getRoomSymbol(RoomType type) {
        switch (type) {
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
}