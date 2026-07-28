package es.boffmedia.teras.shiny;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.shiny.api.ShinyProvider;
import es.boffmedia.teras.shiny.api.ShinyProviders;
import es.boffmedia.teras.util.TerasConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /teras shiny} — says why the cue is or is not firing, and {@code /teras shiny olvidar}
 * re-arms every sighting so a shiny already stood in front of you sparkles again.
 *
 * <p>This exists because the tracker has five independent ways to be silently off — disabled in
 * config, no Pokémon engine, the player in a battle or a dungeon run, nothing shiny in range, or the
 * sighting already spent — and <b>all five look identical from in-game</b>: a shiny that just stands
 * there. That is the failure the combat rebuild already paid for once
 * ({@code mimir 101}); a feature whose whole output is "sometimes a sound plays" should not ship
 * without a way to ask it what it thinks.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID)
public final class ShinyCommand {
    private ShinyCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("teras")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("shiny")
                        .executes(ShinyCommand::status)
                        .then(Commands.literal("olvidar").executes(ShinyCommand::forget))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        TerasConfig.ShinySettings config = TerasConfig.shiny();
        ShinyProvider provider = ShinyProviders.get();
        ServerPlayer player = source.getPlayer();

        StringBuilder sb = new StringBuilder("Rastreador de shinies:\n");
        sb.append("  activado: ").append(config.enabled()).append('\n');
        sb.append("  motor: ").append(provider == null ? "NINGUNO (ni Pixelmon ni Cobblemon)"
                : provider.engineId()).append('\n');
        sb.append("  rango: ").append(config.range()).append(" bloques\n");
        sb.append("  repite cada: ").append(config.repeatSeconds() == 0
                ? "nunca (una sola vez por Pokemon y jugador)" : config.repeatSeconds() + " s").append('\n');
        sb.append("  linea de vision: ").append(config.requireLineOfSight()).append('\n');
        sb.append("  cono de vision: ").append(config.viewConeDegrees() <= 0
                ? "desactivado (cualquier direccion)" : config.viewConeDegrees() + " grados").append('\n');
        sb.append("  particulas: ").append(config.particles())
                .append("   volumen: ").append(config.volume()).append('\n');
        sb.append("  avistamientos recordados: ").append(ShinySpotter.trackedCount());

        if (player != null) {
            // The two per-player gates. Reported even when everything else is on, because these are
            // the ones that change from second to second and are invisible from the outside.
            if (provider != null && provider.isBattling(player)) {
                sb.append("\n  ATENCION: estas en combate — el aviso esta silenciado para ti");
            }
            if (es.boffmedia.teras.dungeon.run.DungeonHealth.isInRun(player)) {
                sb.append("\n  ATENCION: estas en una mazmorra — el aviso esta silenciado para ti");
            }
        }

        String message = sb.toString();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) {
        int count = ShinySpotter.trackedCount();
        ShinySpotter.reset();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Olvidados " + count + " avistamientos; los shinies cercanos volveran a avisar"), true);
        return 1;
    }
}
