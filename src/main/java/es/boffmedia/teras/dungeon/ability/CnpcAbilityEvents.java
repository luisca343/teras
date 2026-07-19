package es.boffmedia.teras.dungeon.ability;

import es.boffmedia.teras.Teras;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.event.NpcEvent;

/**
 * Runs {@link AbilityEngine} for CustomNPCs enemies. Registered on CustomNPCs' <b>own</b> event bus
 * by {@link AbilityBridge}, not through {@code @EventBusSubscriber} — these are not NeoForge game
 * events, and the bus only picks up static methods, exactly as {@code QuestEvents} works.
 *
 * <p>Along with {@code CnpcBridge} this is one of only two dungeon classes allowed to import
 * {@code noppes.npcs.*}; nothing may name it until {@code CnpcBridge.available()} has said yes.</p>
 *
 * <p>This is what replaced attaching {@code .js} to each NPC. CustomNPCs' scripting exposes the
 * same hooks ({@code ATTACK_MELEE}, {@code DAMAGED}, {@code TICK}, {@code DIED}) but the mod ships
 * no script engine and Java 21 has none built in, so scripted abilities are silently dead unless an
 * admin installs one; and attaching a script from code needs {@code EntityNPCInterface.script}, an
 * internal field. Admin-authored scripts still work on top of this — they are not exclusive.</p>
 */
public final class CnpcAbilityEvents {
    private CnpcAbilityEvents() {}

    /** Called only from {@link AbilityBridge}, once the mod check has passed. */
    static void register() {
        try {
            if (!NpcAPI.IsAvailable()) {
                Teras.LOGGER.warn("Dungeons: CustomNPCs is loaded but its API is not ready — "
                        + "NPC abilities disabled");
                return;
            }
            NpcAPI.Instance().events().register(CnpcAbilityEvents.class);
            Teras.LOGGER.info("Dungeons: NPC abilities enabled on the CustomNPCs event bus");
        } catch (Throwable t) {
            Teras.LOGGER.error("Dungeons: could not register NPC abilities: {}", t.toString());
        }
    }

    /** Health thresholds. The NPC bus fires this per NPC per tick, so it stays cheap on purpose. */
    @SubscribeEvent
    public static void onUpdate(NpcEvent.UpdateEvent event) {
        LivingEntity npc = living(event.npc == null ? null : event.npc.getMCEntity());
        if (npc != null) {
            guard(() -> AbilityEngine.tick(npc), "tick");
        }
    }

    /**
     * Melee side effects, plus the one piece of damage maths that cannot live in the shared engine:
     * CustomNPCs computes its hit from {@code INPCMelee.strength}, not from {@code ATTACK_DAMAGE},
     * so an enrage attribute modifier would never reach it. The event's mutable {@code damage} is
     * where that multiplier has to be applied.
     */
    @SubscribeEvent
    public static void onMelee(NpcEvent.MeleeAttackEvent event) {
        LivingEntity npc = living(event.npc == null ? null : event.npc.getMCEntity());
        LivingEntity target = living(event.target == null ? null : event.target.getMCEntity());
        if (npc == null || target == null) {
            return;
        }
        guard(() -> {
            if (AbilityEngine.isEnraged(npc)) {
                for (AbilityDef def : Abilities.of(npc)) {
                    if (def.kind() == AbilityKind.ENRAGE) {
                        event.damage *= (float) (1.0 + def.param("damageMult", 0.5));
                    }
                }
            }
            // Passed after the multiplier so a cleave splashes off the enraged hit, not the base one.
            AbilityEngine.onMeleeHit(npc, target, event.damage);
        }, "melee");
    }

    @SubscribeEvent
    public static void onDamaged(NpcEvent.DamagedEvent event) {
        LivingEntity npc = living(event.npc == null ? null : event.npc.getMCEntity());
        Entity source = event.source == null ? null : event.source.getMCEntity();
        if (npc != null) {
            guard(() -> AbilityEngine.onDamaged(npc, source, event.damage), "damaged");
        }
    }

    private static LivingEntity living(Object mcEntity) {
        return mcEntity instanceof LivingEntity entity ? entity : null;
    }

    /**
     * A broken ability costs that ability, never the NPC's turn and never the run. These handlers
     * sit on CustomNPCs' bus, where an escaping exception would surface as the mod's own failure.
     */
    private static void guard(Runnable body, String hook) {
        try {
            body.run();
        } catch (Exception e) {
            Teras.LOGGER.warn("Dungeons: ability {} hook failed: {}", hook, e.toString());
        }
    }
}
