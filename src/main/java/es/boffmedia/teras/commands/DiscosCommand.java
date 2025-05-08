package es.boffmedia.teras.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import es.boffmedia.teras.util.media.AudioManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;

import java.nio.file.Files;
import java.nio.file.Path;

public class DiscosCommand {
    public DiscosCommand(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("disco")
                .then(crear());


        dispatcher.register(literalBuilder);

    }

    private ArgumentBuilder<CommandSource,?> crear() {
        return Commands.literal("crear")
                .then(Commands.argument("nombre", StringArgumentType.string())
                        .executes((command) -> {
                            String nombre = StringArgumentType.getString(command, "nombre");
                            ServerPlayerEntity player = (ServerPlayerEntity) command.getSource().getEntity();

                            ItemStack disco = player.getItemInHand(Hand.MAIN_HAND);

                            Path patata = AudioManager.getFile(nombre, "wav");

                            if(!Files.exists(patata)){
                                player.sendMessage(new StringTextComponent("No se ha encontrado el disco"), player.getUUID());
                                return 0;
                            }

                            /*
                            if (disco.getItem() == ItemInit.DISCO.get()) {
                                CompoundNBT tags = new CompoundNBT();
                                tags.putString("disco", nombre);
                                disco.setTag(tags);
                            }*/
                            return 1;
                        }
                ));
    }



    /*
    private static VoicechatServerApi SERVER_API;
    public  static FolderName DISCOS = new FolderName("discos");
    public static AudioFormat FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000F, 16, 1, 2, 48000F, false);

    public Discos(CommandDispatcher<CommandSource> dispatcher){
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("sonido")
                .requires((commandSource -> commandSource.hasPermission(3)));

        literalBuilder.then(Commands.literal("descargar")
                        .then(Commands.argument("url", StringArgumentType.string())
                        .then(Commands.argument("nombre", StringArgumentType.string())
                                .executes((command) -> {

                                    CommandSource source = command.getSource();
                            String url = command.getArgument("url", String.class);
                            String nombre = command.getArgument("nombre", String.class);

                                    new Thread(() -> {
                                        try {
                                            source.sendSuccess(new StringTextComponent("Descargando, por favor espera..."), false);
                                            //zAudioManager.guardar(url, nombre, command.getSource().getLevel());
                                            source.sendSuccess(new StringTextComponent("¡Archivo descargado!"), false);

                                        } catch (IOException e) {
                                            source.sendFailure(new StringTextComponent("Error descargando el archivo: " + e.getMessage()));
                                            e.printStackTrace();
                                        } catch (UnsupportedAudioFileException e) {
                                            source.sendFailure(new StringTextComponent("Error. Formato no soportado: " + e.getMessage()));
                                            e.printStackTrace();
                                        }
                                    }).start();


                            //manager.play(command.getSource().getLevel());

                            return 1;
                        })
                    )));


        literalBuilder.then(Commands.literal("jugador")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("nombre", StringArgumentType.string())
                                .executes((command) -> {
                                    ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");

                                    String nombre = command.getArgument("nombre", String.class);
                                    AudioManager manager = new AudioManager();
                                    manager.reproducirJugador(player, nombre);
                                    //manager.play(command.getSource().getLevel());

                                    return 1;
                                })
                        )));


        dispatcher.register(literalBuilder);

        literalBuilder.then(Commands.literal("test")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("nombre", StringArgumentType.string())
                                .executes((command) -> {
                                    ServerPlayerEntity player = EntityArgument.getPlayer(command, "player");

                                    String nombre = command.getArgument("nombre", String.class);
                                    AudioManager manager = new AudioManager();

                                    try {

                                        AudioConverter.AudioType tipo = AudioConverter.getAudioType(AudioManager.getFile(nombre));
                                        command.getSource().sendSuccess(new StringTextComponent("Tipo: " + tipo), false);
                                    } catch (UnsupportedAudioFileException e) {
                                        command.getSource().sendFailure(new StringTextComponent("Error. Formato no soportado: " + e.getMessage()));
                                    } catch (IOException e) {
                                        command.getSource().sendFailure(new StringTextComponent("Error descargando el archivo: " + e.getMessage()));
                                    }
                                    manager.reproducirJugador(player, nombre);

                                    //manager.play(command.getSource().getLevel());

                                    return 1;
                                })
                        )));


        dispatcher.register(literalBuilder);
    }*/
}
