package es.boffmedia.teras.pixelmon.battle;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleProperty;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleRegistry;
import com.pixelmonmod.pixelmon.battles.api.rules.clauses.BattleClause;
import com.pixelmonmod.pixelmon.battles.api.rules.clauses.tiers.Tier;
import com.pixelmonmod.pixelmon.battles.api.rules.property.*;
import com.pixelmonmod.pixelmon.enums.EnumOldGenMode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TerasBattleRuleRegistry extends BattleRuleRegistry {
    private static final Map<String, BattleProperty<?>> REGISTERED_PROPERTIES = Maps.newConcurrentMap();
    public static final BattleProperty<Boolean> RAISE_TO_CAP = register(new RaiseToCapProperty());
    public static final BattleProperty<Boolean> FULL_HEAL = register(new FullHealProperty());
    public static final BattleProperty<Boolean> TEAM_PREVIEW = register(new TeamPreviewProperty());
    public static final BattleProperty<Integer> LEVEL_CAP = register(new LevelCapProperty());
    public static final BattleProperty<Integer> NUM_POKEMON = register(new NumPokemonProperty());
    public static final BattleProperty<Integer> TEAM_SELECT = register(new TeamSelectProperty());
    public static final BattleProperty<Integer> TURN_TIME = register(new TurnTimeProperty());
    public static final BattleProperty<Tier> TIER = register(new TierProperty());
    public static final BattleProperty<Set<BattleClause>> CLAUSES = register(new ClausesProperty());
    public static final BattleProperty<BattleType> BATTLE_TYPE = register(new BattleTypeProperty());
    public static final BattleProperty<EnumOldGenMode> GEN_MODE = register(new OldGenModeProperty());
    public static final BattleProperty<Boolean> SPECIAL_BATTLE = register(new TerasBattleProperty());

    public TerasBattleRuleRegistry() {
    }

    public static <T> BattleProperty<T> register(BattleProperty<T> property) {
        REGISTERED_PROPERTIES.put(property.getId().toLowerCase(Locale.ROOT), property);
        return property;
    }

    public static BattleProperty<?> getProperty(String id) {
        return (BattleProperty)REGISTERED_PROPERTIES.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<BattleProperty<?>> getAllProperties() {
        return Lists.newArrayList(REGISTERED_PROPERTIES.values());
    }
}
