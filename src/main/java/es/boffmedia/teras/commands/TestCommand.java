package es.boffmedia.teras.commands;


import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.species.dimensions.Dimensions;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.entities.pixelmon.StatueEntity;
import com.pixelmonmod.pixelmon.enums.EnumBoundingBoxMode;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.client.CMessageFindPath;
import es.boffmedia.teras.net.client.clientOld.CMessageVerVideo;
import es.boffmedia.teras.net.client.clientOld.CMessageWaypoints;
import es.boffmedia.teras.net.video.ScreenManager;
import es.boffmedia.teras.util.RouteCreator;
import es.boffmedia.teras.util.data.smartrotom.SmartRotomService;
import es.boffmedia.teras.util.objects.quests.UpdateNPCs;
import es.boffmedia.teras.util.objects._old.WayPoint;
import es.boffmedia.teras.util.objects._old.karts.Circuito;
import es.boffmedia.teras.pixelmon.battle.TerasBattleController;
import es.boffmedia.teras.pixelmon.battle.TeamManager;
import es.boffmedia.teras.pixelmon.frentebatalla.TorreBatallaController;
import es.boffmedia.teras.util.file.FileHelper;
import es.boffmedia.teras.util.string.MessageHelper;
import es.boffmedia.teras.util.data.PersistentDataFields;
import es.boffmedia.teras.util.media.AudioManager;
import moe.plushie.armourers_workshop.core.data.LocalDataService;
import moe.plushie.armourers_workshop.core.skin.Skin;
import moe.plushie.armourers_workshop.core.skin.SkinLoader;
import moe.plushie.armourers_workshop.core.skin.exporter.SkinExportManager;
import moe.plushie.armourers_workshop.library.data.SkinLibrary;
import moe.plushie.armourers_workshop.library.data.SkinLibraryManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.io.*;
import java.util.*;

public class TestCommand {
    private Circuito circuito;

    public TestCommand(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("test")
                .then(hitCar())
                .then(npc())
                .then(armadura())
                .then(broadcast())
                .then(peluche())
                .then(registrarEquipo())
                .then(testFB())
                .then(baseSecreta())
                .then(guardarBase())
                .then(guardarEquipo())
                .then(cargarEquipo())
                .then(iniciarCombateFrenteBatalla())
                .then(playVideo())
                .requires((commandSource -> commandSource.hasPermission(3)))
                    .then(crearWaypoint())
                .then(reproducirSonido())
                .then(testset())
                .then(findPath());

        dispatcher.register(literalBuilder);

    }

    private ArgumentBuilder<CommandSource, ?> findPath() {
        return Commands.literal("findPath")
                .executes((command) -> {
                    RouteCreator.Point startPoint = new RouteCreator.Point(46, 14);
                    RouteCreator.Point endPoint = new RouteCreator.Point(130, -9);
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    System.out.println("Enviando mensaje de findPath");
                    Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageFindPath(startPoint, endPoint));
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> hitCar() {
        return Commands.literal("hitcar")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes((command) -> {
                            ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");
                            Teras.raceManager.hitCar(player);
                            //target.getPersistentData().putBoolean(PersistentDataFields.CAR_HIT.label, true);
                            return 1;
                        }
                ));
    }

    private ArgumentBuilder<CommandSource,?> npc() {
        return Commands.literal("npc")
                .executes((command) -> {
                    UpdateNPCs updateNPCs = new UpdateNPCs();
                    SmartRotomService.updateNPCs(updateNPCs);

                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> armadura() {
        return Commands.literal("armadura")
                        .executes((command) -> {
                            SkinLibrary lib = SkinLibraryManager.getServer().getLibrary();
                            System.out.println("=====================================");
                            System.out.println(lib.getRootPath());
                            lib.getFiles().forEach((skinFile) -> {
                                System.out.println(skinFile.getName());
                                System.out.println(skinFile.getPath());
                                System.out.println(skinFile.getSkinIdentifier());
                                System.out.println(skinFile.getSkinType());
                                System.out.println(skinFile.getSkinProperties());


                                try {
                                    Skin skin = SkinLoader.getInstance().loadSkin(skinFile.getSkinIdentifier());
                                    SkinExportManager.exportSkin(skin, "obj", skinFile.getName(), 1.0F);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

                            LocalDataService lib2 = SkinLibraryManager.getServer().getDatabaseLibrary();

                            System.out.println("=====================================");
                            System.out.println(lib2);

                            return 1;
                        }
                );
    }


    private ArgumentBuilder<CommandSource,?> broadcast() {
        return Commands.literal("broadcast")
                .then(Commands.argument("canal", IntegerArgumentType.integer())
                 .then(Commands.argument("url", StringArgumentType.string())
                .executes((command) -> {
                    String url = StringArgumentType.getString(command, "url");
                    int canal = IntegerArgumentType.getInteger(command, "canal");

                    ScreenManager.broadcastVideo(url, canal, command.getSource().getLevel());
                    return 1;
                })));
    }

    private ArgumentBuilder<CommandSource,?> peluche() {
        return Commands.literal("peluche")
                .then(Commands.argument("nombre", StringArgumentType.string())
                .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    String nombre = StringArgumentType.getString(command, "nombre").toLowerCase(Locale.ROOT);
                    StatueEntity entity = new StatueEntity(player.level);
                    entity.setPos(player.getX(), player.getY(), player.getZ());
                    
                    if(!PixelmonSpecies.get(nombre).isPresent()) {
                        MessageHelper.enviarMensaje(player, "No existe el pokemon " + nombre);
                        return 1;
                    };

                    Species species = PixelmonSpecies.get(nombre).get().getValue().get();
                    Dimensions dim = species.getDefaultForm().getDimensions();
                    entity.setSpecies(species);
                    entity.setForm(species.getDefaultForm());
                    entity.setBoundingMode(EnumBoundingBoxMode.None);

                    double mayor = dim.getHeight();
                    if(dim.getLength() > mayor){
                        mayor = dim.getLength();
                    }

                    float escala = (float) (1 / mayor);
                    entity.setPixelmonScale(escala);


                    player.level.addFreshEntity(entity);
                    return 1;
                }));
    }
    
    private ArgumentBuilder<CommandSource,?> testFB() {
        return Commands.literal("testFB")
                .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    if(player.getPersistentData().getBoolean(PersistentDataFields.FB_ACTIVO.label)) {
                        String modalidad = player.getPersistentData().getString(PersistentDataFields.EQUIPO_ACTIVO.label);
                        TorreBatallaController.iniciarCombatev2(player, modalidad);
                        MessageHelper.enviarMensaje(player, "Actualmetne participando en: " + player.getPersistentData().getString(PersistentDataFields.FB_ACTIVO.label));
                    } else{
                        MessageHelper.enviarMensaje(player, "No tienes un equipo registrado en el frente de batalla");
                    }
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> registrarEquipo() {
        return Commands.literal("registrarEquipo")
                        .executes((command) -> {
                            ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                            TorreBatallaController.registrarEquipo(TerasBattleController.TipoCombate.TB_INDIVIDUAL.label, player);
                            return 1;
                        });
    }

    public int parcelasCreadas = 0;
    public BlockVector3 inicio = BlockVector3.at(50, 100, 100);

    private ArgumentBuilder<CommandSource,?> baseSecreta() {
        return Commands.literal("baseSecreta")
                .executes((command) -> {
                    PlayerEntity player = (PlayerEntity) command.getSource().getEntity();
                    File schem = FileHelper.getSchematic("baseSecreta");
                    try {
                        ClipboardReader reader = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getReader(new FileInputStream(schem));
                        Clipboard clipboard = reader.read();

                        World world = ForgeAdapter.adapt(player.level);
                        EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, -1);

                        BlockVector3 inicioNueva = inicio.add(0, 0, 15 * parcelasCreadas);
                        Operation operation = new ClipboardHolder(clipboard)
                                .createPaste(editSession)
                                .to(inicioNueva)
                                .ignoreAirBlocks(false)
                                .build();
                        try {
                            Operations.complete(operation);
                            MessageHelper.enviarMensaje(player, "Parcela creada: " + ++parcelasCreadas);
                            editSession.flushSession();
                        } catch (WorldEditException e) {
                            e.printStackTrace();
                        }

                        return 1;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                });
    }

    private ArgumentBuilder<CommandSource,?> guardarBase() {
        return Commands.literal("guardarBase")
                .executes((command) -> {
                        PlayerEntity player = (PlayerEntity) command.getSource().getEntity();

                    World world = ForgeAdapter.adapt(player.level);
                    BlockVector3 pos1 = BlockVector3.at(0, 0, 0);
                    BlockVector3 pos2 = BlockVector3.at(10, 10, 10);

                    CuboidRegion region = new CuboidRegion(world, pos1, pos2);
                    Clipboard clipboard = new BlockArrayClipboard(region);


                    ForwardExtentCopy forwardExtentCopy = new ForwardExtentCopy(
                            world, region, clipboard, region.getMinimumPoint()
                    );


                    try {
                        Operations.complete(forwardExtentCopy);
                        MessageHelper.enviarMensaje(player, "OPERACION COMPLETADA, BLOQUES AFECTADOS: " + forwardExtentCopy.getAffected());

                        File schem = FileHelper.getSchematic("baseSecreta");


                        ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(schem));
                        writer.write(clipboard);
                        writer.close();


                    } catch (WorldEditException e) {
                        throw new RuntimeException(e);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return 1;

                });
    }

    private ArgumentBuilder<CommandSource,?> iniciarCombateFrenteBatalla() {
        return Commands.literal("iniciarCombateFrenteBatalla")
                .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    TorreBatallaController.iniciarCombate(player);


                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource,?> cargarEquipo() {
        return Commands.literal("cargarEquipo")
                .then(Commands.argument("nombre", StringArgumentType.string())
                .executes((command) -> {
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    String nombre = StringArgumentType.getString(command, "nombre");

                    TeamManager.loadTeam(player, "equipo");
                    player.getPersistentData().putBoolean(PersistentDataFields.FB_ACTIVO.label, false);

                    return 1;
                }));
    }

    private ArgumentBuilder<CommandSource,?> guardarEquipo() {
        return Commands.literal("guardarEquipo")
                .then(Commands.argument("nombre", StringArgumentType.string())
                .executes((command) -> {
                       ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                       String nombre = StringArgumentType.getString(command, "nombre");

                       TeamManager.saveTeam(player, nombre);

              return 1;
           }
           ));
    }

    private ArgumentBuilder<CommandSource,?> playVideo() {
        return Commands.literal("playvideo")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("url", StringArgumentType.string())
                        .executes((command) -> {
                            ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                            String url = StringArgumentType.getString(command, "url");
                            Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageVerVideo(url));


                            return 1;
                        }
                )));
    }

    private ArgumentBuilder<CommandSource,?> testset() {
        return Commands.literal("testset")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes((command) -> {

                            return 1;
                        }
                ));
    }

    private ArgumentBuilder<CommandSource,?> reproducirSonido() {
        return Commands.literal("reproducir")
                        .executes((command) -> {
                            ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                            AudioManager audioManager = new AudioManager();
                            audioManager.playMp3(player);
                            return 1;
                        }
                );
    }

    private ArgumentBuilder<CommandSource, ?> crearWaypoint() {
        return Commands.literal("waypoint")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer())
                .then(Commands.argument("nombre", StringArgumentType.string())
                .then(Commands.argument("color", StringArgumentType.string())
                .executes((command) -> {
                    command.getSource().getEntity().sendMessage(new StringTextComponent("ESTO ES UNA PRUEBA"), UUID.randomUUID());
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    int x = IntegerArgumentType.getInteger(command, "x");
                    int y = IntegerArgumentType.getInteger(command, "y");
                    int z = IntegerArgumentType.getInteger(command, "z");
                    String nombre = StringArgumentType.getString(command, "nombre");
                    String color = StringArgumentType.getString(command, "color");

                    WayPoint waypoint = new WayPoint( x, y, z, "world",color, nombre);
                    Gson gson = new Gson();
                    Messages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new CMessageWaypoints(gson.toJson(waypoint)));

                    return 1;
                }))))));
    }
}