package es.boffmedia.teras.pixelmon.battle.handlers;

import com.pixelmonmod.pixelmon.battles.controller.log.action.BattleAction;
import com.pixelmonmod.pixelmon.battles.controller.log.action.type.*;
import es.boffmedia.teras.Teras;

import java.util.HashMap;
import java.util.Map;

public class BattleActionHandlerFactory {
    private static final Map<Class<? extends BattleAction>, BattleActionHandler<?>> handlers = new HashMap<>();

    static {
        register(TurnBeginAction.class, new TurnBeginActionHandler());
        register(BattleEndAction.class, new BattleEndActionHandler());
        register(SwitchAction.class, new SwitchActionHandler());
        register(AttackAction.class, new AttackActionHandler());
        register(StatChangeAction.class, new StatChangeActionHandler());
        register(WeatherChangeAction.class, new WeatherChangeActionHandler());
        register(StatusAddAction.class, new StatusAddActionHandler());
        register(StatusRemoveAction.class, new StatusRemoveActionHandler());
        register(TerrainChangeAction.class, new TerrainChangeActionHandler());
        register(BattleMessageAction.class, new BattleMessageActionHandler());
        register(DamagePokemonAction.class, new DamagePokemonActionHandler());
        register(HealPokemonAction.class, new HealPokemonActionHandler());
        register(GlobalStatusAddAction.class, new GlobalStatusAddActionHandler());
        register(GlobalStatusRemoveAction.class, new GlobalStatusRemoveActionHandler());
        register(MegaEvolveAction.class, new MegaEvolveActionHandler());
        register(HeldItemChangeAction.class, new HeldItemChangeActionHandler());
        register(ChangeAbilityAction.class, new ChangeAbilityActionHandler());
        register(ChangeTypeAction.class, new ChangeTypeActionHandler());
        register(TurnEndAction.class, new TurnEndActionHandler());
        register(EnterDynamaxAction.class, new EnterDynamaxActionHandler());
        register(ExitDynamaxAction.class, new ExitDynamaxActionHandler());
        register(UltraBurstAction.class, new UltraBurstActionHandler());
        register(BagItemAction.class, new BagItemActionHandler());
    }

    private static <T extends BattleAction> void register(Class<T> clazz, BattleActionHandler<T> handler) {
        handlers.put(clazz, handler);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BattleAction> BattleActionHandler<T> getHandler(T action) {
        BattleActionHandler<T> handler = (BattleActionHandler<T>) handlers.get(action.getClass());
        if (handler == null) {
            Teras.LOGGER.warn("Unknown action: " + action.getClass().getSimpleName());
            return null;
        }
        return handler;
    }
}