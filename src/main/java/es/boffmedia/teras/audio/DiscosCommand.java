package es.boffmedia.teras.audio;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.ItemInit;
import es.boffmedia.teras.items.Disco;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /disco} — the server's record library, and stamping discs from it.
 *
 * <p>Admin-gated at level 3 throughout, deliberately: {@code descargar} writes an arbitrary URL's
 * bytes to the server's disk and everything downstream can only choose from what it wrote, so this
 * is the one checkpoint where what exists is decided. On a server running LuckPerms the level check
 * is what LuckPerms overrides, so the gate is manageable there without any extra plumbing here.</p>
 *
 * <p>1.16.5 registered {@code /disco crear} and it did nothing: it checked a {@code .wav} existed
 * and then hit two commented-out lines where the NBT write should have been. {@code descargar} and
 * the per-player playback commands existed only inside a ~90 line commented block.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class DiscosCommand {
    private DiscosCommand() {}

    /** Level 3, not the level 2 the authoring commands use: this one writes to disk from the net. */
    private static final int PERMISSION_LEVEL = 3;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("disco")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("descargar")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .executes(DiscosCommand::download))))
                .then(Commands.literal("crear")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(DiscosCommand::create)))
                .then(Commands.literal("listar").executes(DiscosCommand::list))
                .then(Commands.literal("borrar")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(DiscosCommand::delete))));
    }

    /**
     * Fetches a URL into the library.
     *
     * <p>Runs on a worker: a download is seconds of blocking IO and the server thread cannot wait
     * for it. Both replies therefore come back through {@code server.execute}, so nothing touches
     * the command source off-thread.</p>
     */
    private static int download(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String url = StringArgumentType.getString(ctx, "url");
        String name = StringArgumentType.getString(ctx, "nombre");

        if (TrackName.normalize(name) == null) {
            source.sendFailure(Component.literal(
                    "Nombre inválido. Sólo minúsculas, números, guiones y guiones bajos (máx. "
                            + TrackName.MAX_LENGTH + ")."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Descargando…").withStyle(ChatFormatting.GRAY), false);
        CompletableFuture.runAsync(() -> {
            try {
                Path saved = AudioLibrary.download(url, name);
                // Decoded straight away rather than lazily on first play: a file that downloads but
                // will not decode should be an error the admin sees now, not silence in a bar later.
                TrackCache.invalidate(TrackName.normalize(name));
                short[] samples = TrackCache.get(name, es.boffmedia.teras.voice.TerasVoicechatPlugin.api());
                String summary = String.format("«%s» descargado (%.0f s)",
                        TrackName.normalize(name), Pcm.seconds(samples));
                source.getServer().execute(() -> source.sendSuccess(
                        () -> Component.literal(summary).withStyle(ChatFormatting.GREEN), true));
                Teras.LOGGER.info("Discos: '{}' ready at {}", TrackName.normalize(name), saved);
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                source.getServer().execute(() -> source.sendFailure(
                        Component.literal("No se pudo descargar: " + reason)));
            }
        });
        return 1;
    }

    /** Stamps the held blank disc with a track that already exists in the library. */
    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer admin = source.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "nombre");

        String track = TrackName.normalize(name);
        if (track == null) {
            source.sendFailure(Component.literal("Nombre inválido."));
            return 0;
        }
        if (!AudioLibrary.exists(track)) {
            source.sendFailure(Component.literal(
                    "No hay ningún disco llamado «" + track + "». Usa /disco listar."));
            return 0;
        }

        ItemStack held = admin.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.is(ItemInit.DISCO.get())) {
            source.sendFailure(Component.literal("Sostén un disco en la mano principal."));
            return 0;
        }

        Disco.setTrack(held, track);
        source.sendSuccess(() -> Component.literal("Disco grabado: «" + track + "»")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> tracks = AudioLibrary.list();
        if (tracks.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "La biblioteca está vacía. Usa /disco descargar <url> <nombre>.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                tracks.size() + " disco(s): " + String.join(", ", tracks)), false);
        return tracks.size();
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nombre");
        try {
            if (!AudioLibrary.delete(name)) {
                ctx.getSource().sendFailure(Component.literal("No existe «" + name + "»."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Borrado «" + name + "»."), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("No se pudo borrar: " + e.getMessage()));
            return 0;
        }
    }
}
