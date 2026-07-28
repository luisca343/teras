package es.boffmedia.teras.dungeon.combat;

import es.boffmedia.teras.dungeon.build.DungeonsConfig;
import es.boffmedia.teras.dungeon.gear.GearDef;
import es.boffmedia.teras.dungeon.gear.GearHolder;
import es.boffmedia.teras.dungeon.gear.GearKind;
import es.boffmedia.teras.dungeon.instance.DungeonRun;
import es.boffmedia.teras.dungeon.instance.DungeonRunManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Answers "why is nothing happening" in one line each.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every part of the rebuilt loop is gated on the same three conditions, and all three fail
 * <b>silently and identically</b>: combat off, not in a run, or in a run but in the wrong dimension
 * all produce a game that behaves exactly as it did before — no error, no warning, no partial
 * behaviour. That is indistinguishable from a feature that was never wired, which is precisely the
 * report this was written after.</p>
 *
 * <p>So the gates report themselves. A check that can only be verified by reading the source is a
 * check the operator cannot use.</p>
 */
public final class CombatDiagnostic {
    private CombatDiagnostic() {}

    /** Every line the player should see, worst news first. */
    public static List<Component> report(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6=== Combate reconstruido ==="));

        boolean enabled = DungeonsConfig.combatEnabled();
        lines.add(flag("combate.activado", enabled,
                "el daño lo calcula Teras", "TODO está apagado: el daño lo calcula Minecraft"));
        if (!enabled) {
            // One line per line of YAML: chat has no newlines inside a component, and a literal \n
            // is what the player actually sees if you try.
            lines.add(Component.literal("§7  En §fconfig/teras/dungeons/config.yml§7, pon:"));
            lines.add(Component.literal("§7    §fcombate:"));
            lines.add(Component.literal("§7      §factivado: true"));
            lines.add(Component.literal("§7  …y reinicia. Si el bloque no aparece en tu archivo es "
                    + "porque los valores de fábrica solo crean un archivo que NO existe todavía."));
        }

        DungeonRun run = DungeonRunManager.runOf(player.getUUID());
        lines.add(flag("en una run", run != null,
                run == null ? "" : "piso " + run.stage(), "nada de esto se aplica fuera de una mazmorra"));

        String here = player.serverLevel().dimension().location().toString();
        boolean rightDimension = here.equals(DungeonsConfig.dimension());
        lines.add(flag("dimensión", rightDimension, here,
                "estás en §f" + here + "§7 y la mazmorra es §f" + DungeonsConfig.dimension()));

        boolean live = enabled && run != null && rightDimension;
        if (!live) {
            lines.add(Component.literal("§cNo se está aplicando nada de lo de abajo."));
        }

        // The weapon no longer gates a verb — PESADO was dropped, and the light chain runs with
        // whatever is in your hand — so this reports the sheet's source rather than a capability.
        GearDef weapon = GearHolder.defOf(player.getMainHandItem());
        lines.add(flag("arma de mazmorra en mano", weapon != null,
                weapon == null ? "" : weapon.id() + " (" + weapon.kind() + ")",
                "no llevas equipo de mazmorra: pegas con las estadísticas base, sin crítico ni "
                        + "penetración"));

        long now = player.serverLevel().getGameTime();
        lines.add(Component.literal("§7esquiva: §f"
                + (Dodge.invulnerable(player) ? "§ainvulnerable ahora" : "lista")
                + "§7 · tecla §fV§7 (reasignable en Controles)"));

        // Named from the lang file, so the sheet reads in the operator's language and the twelve
        // stat.teras.* keys the panel cannot use (it draws icons) have somewhere to be read.
        StatBlock sheet = CombatSheets.of(player);
        MutableComponent stats = Component.literal("");
        for (Stat stat : Stat.values()) {
            stats.append(Component.translatableWithFallback("stat.teras." + stat.key(), stat.key())
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(
                                    " " + String.format(Locale.ROOT, "%.2f", sheet.get(stat)) + "  ")
                            .withStyle(ChatFormatting.WHITE));
        }
        lines.add(stats);
        lines.add(Component.literal("§8tick " + now + " · el panel se sincroniza cada segundo"));
        return lines;
    }

    private static Component flag(String name, boolean ok, String detail, String problem) {
        String suffix = ok
                ? (detail == null || detail.isBlank() ? "" : " §7(" + detail + ")")
                : " §7— " + problem;
        return Component.literal((ok ? "§a✔ " : "§c✘ ") + "§f" + name + suffix);
    }
}
