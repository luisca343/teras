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
 * {@code /npcs escanear} — sweeps every world for CustomNPCs and refreshes the persisted catalog.
 * Port of the 1.16.5 {@code /test npc} subcommand, which was the only trigger for the full scan.
 *
 * <p>The catalog is what gives each dialog its {@code npcLocations} (name, skin, coordinates) when
 * {@code QuestService} builds {@code /quests/all}, so run this after moving or adding NPCs. Dialog
 * opens keep it current for NPCs players actually talk to; this catches the rest.</p>
 *
 * <p>No longer POSTs to SmartRotom: NPC data now reaches the backend inside the quest catalog, so the
 * separate {@code /smartrotom/misiones/npcs} push was redundant (and its body never matched the
 * backend's {@code UpdateNPCsDto} anyway).</p>
 *
 * <p>Admin-only (permission level 2, the same bar {@code chatMessage} uses): it walks every loaded
 * entity and rewrites a config file.</p>
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
        // Walks every loaded level's entities, so it runs on the command (server) thread.
        int count = QuestScan.run();
        source.sendSuccess(() -> Component.literal(
                "Catálogo de NPCs actualizado (" + count + " diálogos)"), true);
        return 1;
    }

    /** Isolates the CustomNPCs-touching call so {@link NpcCommand} itself stays free of them. */
    private static final class QuestScan {
        private QuestScan() {}

        static int run() {
            return NpcScanner.scanAll().size();
        }
    }
}
