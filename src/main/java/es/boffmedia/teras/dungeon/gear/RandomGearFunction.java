package es.boffmedia.teras.dungeon.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import es.boffmedia.teras.Teras;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.Map;

/**
 * A loot entry that says <i>how rare</i> and lets the catalog say <i>which</i>.
 *
 * <p>Written {@code {"function": "teras:gear_aleatorio", "comun": 50, "raro": 35, "epico": 15}}, it
 * replaces the stack it is applied to with a fully stamped piece drawn at those odds. The entry's
 * own item is a placeholder and is discarded — a {@link net.minecraft.world.level.storage.loot.functions.LootItemFunction}
 * returns a stack rather than mutating one, so it may hand back a different item entirely, which is
 * exactly what picking across gear kinds needs.</p>
 *
 * <p>Why it exists at all is in {@link GearRoll}: the tables used to name pieces, which made them a
 * second catalog kept in step by hand, left {@link GearDef.Rarity} meaning nothing, and made a piece
 * added to {@code gear.json} undroppable forever.</p>
 *
 * <p>Built through {@link GearItems#create} rather than by stamping a component onto a base item,
 * so a drop is identical to what {@code /teras dungeon gear dar} produces and to what migration
 * would rebuild. Three ways of making the same piece that could disagree is how the tables drifted
 * in the first place.</p>
 */
public class RandomGearFunction extends LootItemConditionalFunction {

    public static final MapCodec<RandomGearFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> commonFields(instance)
                    .and(instance.group(
                            Codec.INT.optionalFieldOf("comun", 50).forGetter(f -> f.odds.comun()),
                            Codec.INT.optionalFieldOf("raro", 35).forGetter(f -> f.odds.raro()),
                            Codec.INT.optionalFieldOf("epico", 15).forGetter(f -> f.odds.epico())))
                    .apply(instance, RandomGearFunction::new));

    private final GearRoll.Odds odds;

    protected RandomGearFunction(List<LootItemCondition> conditions, int comun, int raro, int epico) {
        super(conditions);
        this.odds = new GearRoll.Odds(comun, raro, epico);
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return LootInit.RANDOM_GEAR.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Map<String, GearDef> catalog = GearDefs.all();
        String id = GearRoll.roll(odds, GearRoll.byRarity(catalog),
                context.getRandom().nextDouble(), context.getRandom().nextDouble());
        if (id == null) {
            // Only reachable with an empty catalog or all-zero weights, both of which are a server
            // having configured the gear away. Saying so beats a blank stack nobody can explain.
            Teras.LOGGER.warn("Dungeons: gear_aleatorio drew nothing — the catalog has no piece for "
                    + "weights {}/{}/{}", odds.comun(), odds.raro(), odds.epico());
            return ItemStack.EMPTY;
        }
        GearDef def = catalog.get(id);
        return def == null ? ItemStack.EMPTY : GearItems.create(def);
    }
}
