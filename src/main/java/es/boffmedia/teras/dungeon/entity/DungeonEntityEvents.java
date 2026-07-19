package es.boffmedia.teras.dungeon.entity;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.EntityInit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Mod-bus registration for the animated dungeon enemy's attributes. */
@EventBusSubscriber(modid = Teras.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DungeonEntityEvents {
    private DungeonEntityEvents() {}

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EntityInit.DUNGEON_ENEMY.get(), DungeonGeoEnemy.createAttributes().build());
    }
}
