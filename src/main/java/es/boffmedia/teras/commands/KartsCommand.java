package es.boffmedia.teras.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgePlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.objects._old.karts.Circuito;
import es.boffmedia.teras.util.objects._old.karts.Punto;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.UUID;

public class KartsCommand {
    private Circuito circuito;

    public KartsCommand(CommandDispatcher<CommandSource> dispatcher){
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("karts")
                .then(votarCarrera())
                .then(desvotarCarrera())
                .then(salir())
                .then(Commands.literal("carrera")
                        .then(entrarCarrera())
                        .then(salirCarrera())
                        .then(cuentaAtras())
                        .then(iniciarCarrera())
                        .then(raceStatus())
                )
                .then(Commands.literal("circuito")
                        .requires((commandSource -> commandSource.hasPermission(3)))
                        .then(crearCircuito())
                        .then(nuevoInicio())
                        .then(nuevoCheckpoint())
                        .then(guardarCircuito())
                        .then(listaCircuitos())
                )
                ;

        dispatcher.register(literalBuilder);

    }



    public boolean esJugador(CommandContext<CommandSource> command) {
        Entity entity = command.getSource().getEntity();
        if (entity instanceof ServerPlayerEntity) {
            entity.sendMessage(new StringTextComponent("Solo los NPC de TokiKarts pueden ejecutar ese comando."), UUID.randomUUID());
            return true;
        }
        return false;
    }

    private ArgumentBuilder<CommandSource,?> listaCircuitos() {
        return Commands.literal("listar")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Listando circuitos..."), UUID.randomUUID());
                        Teras.raceManager.listTracks(player);
                        return 1;
                    }
                    return 0;
                });
    }

    private ArgumentBuilder<CommandSource,?> votarCarrera() {
        return Commands.literal("votar")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Votando..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.voteStart(player);
                        return 1;
                    }
                    return 0;
                });
    }


    private ArgumentBuilder<CommandSource,?> desvotarCarrera() {
        return Commands.literal("cancelarvoto")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Desvotando..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.unvoteStart(player);
                        return 1;
                    }
                    return 0;
                });
    }


    private ArgumentBuilder<CommandSource,?> salir() {
        return Commands.literal("salir")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        player.sendMessage(new StringTextComponent("Saliendo..." + player.getUUID()), UUID.randomUUID());
                        Teras.raceManager.leaveRace(player);
                        return 1;
                    }
                    return 0;
                });
    }

    private ArgumentBuilder<CommandSource,?> cuentaAtras() {
        return Commands.literal("cuentaAtras")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            if(esJugador(command)) return 0;
                            String nombre = StringArgumentType.getString(command, "nombre");

                            Teras.raceManager.startCountdown(nombre);
                            return 1;
                        })
                );
    }

    private ArgumentBuilder<CommandSource,?> iniciarCarrera() {
        return Commands.literal("iniciar")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            if(esJugador(command)) return 0;
                            String nombre = StringArgumentType.getString(command, "nombre");
                            // Usar el CarreraManager para crear la carrera
                            Teras.raceManager.startRace(nombre);
                            return 1;
                        })
                );
    }

    private ArgumentBuilder<CommandSource,?> salirCarrera() {
        return Commands.literal("salir")
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes((command) -> {
                            ServerPlayerEntity player = EntityArgument.getPlayer(command, "jugador");
                            Teras.raceManager.leaveRace(player);
                            return 1;
                        })
                );


    }

    private ArgumentBuilder<CommandSource,?> entrarCarrera() {
        return Commands.literal("entrar")
                .then(Commands.argument("jugador", EntityArgument.player())
                        .then(Commands.argument("nombre", StringArgumentType.string())
                                .then(Commands.argument("vueltas", IntegerArgumentType.integer())
                                        .executes((command) -> {
                                            if(esJugador(command)) return 0;
                                            int vueltas = IntegerArgumentType.getInteger(command, "vueltas");
                                            String nombre = StringArgumentType.getString(command, "nombre");
                                            ServerPlayerEntity player = EntityArgument.getPlayer(command, "jugador");


                                            // Usar el CarreraManager para crear la carrera
                                            Teras.raceManager.joinRace(nombre, vueltas, player);


                                            return 1;
                                        })
                                )));
    }


    private ArgumentBuilder<CommandSource,?> guardarCircuito() {
        return Commands.literal("guardar")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (circuito == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }

                    if(circuito.getCheckpoints().size() < 2){
                        source.sendFailure(new StringTextComponent("El circuito debe tener al menos 2 checkpoints"));
                        return 0;
                    }

                    if(circuito.getInicios().size() < 1){
                        source.sendFailure(new StringTextComponent("El circuito debe tener al menos 1 punto de inicio"));
                        return 0;
                    }

                    circuito.guardar();
                    source.sendSuccess(new StringTextComponent("Circuito guardado"), false);
                    return 1;
                });
    }

    public LiteralArgumentBuilder<CommandSource> crearCircuito(){
        return Commands.literal("crear")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            String nombre = StringArgumentType.getString(command, "nombre");
                            circuito = new Circuito(nombre);
                            return 1;
                        })
                );
    }

    public LiteralArgumentBuilder<CommandSource> nuevoCheckpoint(){
        return Commands.literal("checkpoint")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (circuito == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    SessionManager manager = WorldEdit.getInstance().getSessionManager();
                    ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                    try {
                        Region selection = manager.get(adaptedPlayer).getSelection();
                        BlockVector3 punto1 = selection.getMinimumPoint();
                        BlockVector3 punto2 = selection.getMaximumPoint();

                        Punto puntoInicio = new Punto(punto1.getX(), punto1.getY(), punto1.getZ());
                        Punto puntoFin = new Punto(punto2.getX(), punto2.getY(), punto2.getZ());
                        source.sendSuccess(new StringTextComponent("Añadiendo..."), false);
                        source.sendSuccess(new StringTextComponent(circuito.toString()), false);

                        circuito.nuevoCheckpoint(puntoInicio, puntoFin);
                        source.sendSuccess(new StringTextComponent("Checkpoint añadido"), false);

                        circuito.printCheckpoints(source);

                    } catch (IncompleteRegionException e) {
                        source.sendFailure(new StringTextComponent("Selecciona una región primero"));
                        e.printStackTrace();
                    }
                    return 1;
                });
    }


    private ArgumentBuilder<CommandSource,?> nuevoInicio() {
        return Commands.literal("inicio")
                .executes((command) -> {
                    CommandSource source = command.getSource();
                    if (circuito == null) {
                        source.sendFailure(new StringTextComponent("Crea un circuito primero con /karts circuito crear <nombre>"));
                        return 0;
                    }
                    ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();
                    SessionManager manager = WorldEdit.getInstance().getSessionManager();
                    ForgePlayer adaptedPlayer = ForgeAdapter.adaptPlayer(player);
                    try {
                        Region selection = manager.get(adaptedPlayer).getSelection();
                        BlockVector3 v1 = selection.getMinimumPoint();

                        Punto punto = new Punto(v1.getX(), v1.getY(), v1.getZ());

                        source.sendSuccess(new StringTextComponent("Añadiendo..."), false);
                        source.sendSuccess(new StringTextComponent(circuito.toString()), false);

                        circuito.nuevoInicio(punto);
                        source.sendSuccess(new StringTextComponent("Inicio añadido"), false);

                        circuito.printInicios(source);


                    } catch (IncompleteRegionException e) {
                        source.sendFailure(new StringTextComponent("Selecciona una región primero"));
                        e.printStackTrace();
                    }
                    return 1;
                });
    }

    private ArgumentBuilder<CommandSource, ?> raceStatus() {
        return Commands.literal("status")
                .executes((command) -> {
                    Entity entity = command.getSource().getEntity();
                    if (entity instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = (ServerPlayerEntity) entity;
                        Teras.raceManager.displayRaceStatus(player);
                        return 1;
                    }
                    return 0;
                });
    }
}

