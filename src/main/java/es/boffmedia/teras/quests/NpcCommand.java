package es.boffmedia.teras.quests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import es.boffmedia.teras.Teras;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /npcs escanear} — sweeps every world for CustomNPCs, refreshes the catalog, and publishes it
 * to SmartRotom. Port of the 1.16.5 {@code /test npc} subcommand, which was the only trigger for the
 * full scan.
 *
 * <p>Admin-only (permission level 2, the same bar {@code chatMessage} uses) because it publishes NPC
 * positions to an external service.</p>
 *
 * <p>This class is registered unconditionally — it holds no CustomNPCs types itself, and the command
 * body checks {@link QuestBridge#isAvailable()} before reaching {@link NpcScanner}, so the mod's
 * absence is reported to the caller rather than crashing.</p>
 */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class NpcCommand {
    private NpcCommand() {}

    private static final int PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("npcs")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("escanear").executes(NpcCommand::escanear)));
    }

    private static int escanear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!QuestBridge.isAvailable()) {
            source.sendFailure(Component.literal("CustomNPCs no está instalado en este servidor"));
            return 0;
        }
        // The scan walks every loaded level's entities, so it stays on the server thread; the upload
        // itself is fire-and-forget on Teras.EXECUTOR (see HttpText.postJson).
        int count = QuestScan.run();
        source.sendSuccess(() -> Component.literal(
                "Catálogo de NPCs actualizado y enviado a SmartRotom (" + count + " diálogos)"), true);
        return 1;
    }

    /** Isolates the CustomNPCs-touching call so {@link NpcCommand} itself stays free of them. */
    private static final class QuestScan {
        private QuestScan() {}

        static int run() {
            var catalog = NpcScanner.scanAll();
            es.boffmedia.teras.util.net.SmartRotomService.updateNpcs(catalog);
            return catalog.size();
        }
    }
}
