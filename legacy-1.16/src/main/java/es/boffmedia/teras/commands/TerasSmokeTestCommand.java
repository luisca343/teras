package es.boffmedia.teras.commands;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import es.boffmedia.teras.pixelmon.battle.TerasBattleLog;
import es.boffmedia.teras.util.file.Reader;
import es.boffmedia.teras.util.objects.pixelmon.BattleConfig;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game self-check for the battle-log subsystem. A Forge mod can't easily run JUnit (Pixelmon/MC
 * classes need a live runtime), so this exercises the fragile pure-logic pieces — identifier
 * validation, the cached reflection helper, and {@link BattleConfig} null-safety against GSON (which
 * bypasses the constructor) — on the running server and reports PASS/FAIL. Run {@code /terassmoketest}.
 */
public class TerasSmokeTestCommand {

    public TerasSmokeTestCommand(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("terassmoketest")
                .requires(src -> src.hasPermission(2))
                .executes(this::run));
    }

    /** Holds a private field so the reflection helper has something non-trivial to read. */
    private static final class Probe {
        @SuppressWarnings("unused") // read via getProtectedProperty, not directly
        private final String secret = "ok";
    }

    private int run(CommandContext<CommandSource> ctx) {
        CommandSource source = ctx.getSource();
        List<String> failures = new ArrayList<>();
        int total = 0;

        // 1. Identifier validation (path-injection guard).
        total++; check(failures, "valid id accepted", Reader.isValidIdentifier("gym_leader-1"));
        total++; check(failures, "slash rejected", !Reader.isValidIdentifier("a/b"));
        total++; check(failures, "traversal rejected", !Reader.isValidIdentifier("../secret"));
        total++; check(failures, "empty rejected", !Reader.isValidIdentifier(""));
        total++; check(failures, "null rejected", !Reader.isValidIdentifier(null));

        // 2. Cached reflection helper reads a private field (twice, to exercise the cache path).
        Probe probe = new Probe();
        total++; check(failures, "reflection read #1",
                "ok".equals(TerasBattleLog.getProtectedProperty("secret", probe)));
        total++; check(failures, "reflection read #2 (cached)",
                "ok".equals(TerasBattleLog.getProtectedProperty("secret", probe)));

        // 3. BattleConfig null-safety. GSON DOES call the no-arg constructor, so fields it defaults
        //    (modalidad="doble") survive an empty object, while fields it doesn't default (tamanoEquipos,
        //    carpeta) stay null — those are the ones the accessors must tolerate without NPE.
        try {
            BattleConfig empty = new Gson().fromJson("{}", BattleConfig.class);
            total++; check(failures, "empty config -> DOUBLE (ctor default)", empty.getBattleType() == BattleType.DOUBLE);
            total++; check(failures, "empty config player size default 6", empty.getPlayerPkmCount() == 6);
            total++; check(failures, "empty config rival size default 6", empty.getRivalPkmCount() == 6);
            total++; check(failures, "empty config IA non-null", empty.getIA() != null);
            total++; check(failures, "empty config normas empty", empty.getNormas().isEmpty());
            total++; check(failures, "empty config gimmick empty", empty.getGimmick().isEmpty());
            total++; check(failures, "empty config esEntrenador", empty.esEntrenador());
        } catch (Exception e) {
            total++; failures.add("empty config threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // 4. BattleConfig parses a populated, asymmetric config.
        try {
            BattleConfig cfg = new Gson().fromJson("{\"modalidad\":\"doble\",\"tamanoEquipos\":\"3vs6\"}", BattleConfig.class);
            total++; check(failures, "doble -> DOUBLE", cfg.getBattleType() == BattleType.DOUBLE);
            total++; check(failures, "3vs6 player=3", cfg.getPlayerPkmCount() == 3);
            total++; check(failures, "3vs6 rival=6", cfg.getRivalPkmCount() == 6);
        } catch (Exception e) {
            total++; failures.add("populated config threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        int passed = total - failures.size();
        if (failures.isEmpty()) {
            source.sendSuccess(new StringTextComponent(
                    TextFormatting.GREEN + "Teras smoke test: " + passed + "/" + total + " checks passed."), false);
        } else {
            source.sendFailure(new StringTextComponent(
                    TextFormatting.RED + "Teras smoke test: " + passed + "/" + total + " passed, " + failures.size() + " FAILED:"));
            for (String f : failures) {
                source.sendFailure(new StringTextComponent(TextFormatting.RED + "  - " + f));
            }
        }
        return failures.isEmpty() ? 1 : 0;
    }

    private static void check(List<String> failures, String name, boolean condition) {
        if (!condition) failures.add(name);
    }
}
