package es.boffmedia.teras.mixin;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.pixelmonmod.api.registry.RegistryValue;
import com.pixelmonmod.pixelmon.api.battles.attack.AttackRegistry;
import com.pixelmonmod.pixelmon.api.events.PokeBallImpactEvent;
import com.pixelmonmod.pixelmon.api.pokemon.LearnMoveController;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.registries.PixelmonBlocks;
import com.pixelmonmod.pixelmon.api.registries.PixelmonForms;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.attacks.ImmutableAttack;
import com.pixelmonmod.pixelmon.entities.pixelmon.interactions.custom.ShearInteraction;
import com.pixelmonmod.pixelmon.entities.pokeballs.OccupiedPokeBallEntity;
import com.pixelmonmod.pixelmon.listener.RotomListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import net.minecraft.block.Blocks;

import java.util.Objects;
import java.util.Optional;

@Mixin(RotomListener.class)
public class RotomListenerMixin {
    private static BiMap<Block, String> blockMap = HashBiMap.create();
    private static BiMap<String, Optional<ImmutableAttack>> attackMap = HashBiMap.create();

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static BiMap<Block, String> getBlockImpactMap() {
        blockMap.put(Blocks.FURNACE, "heat");
        blockMap.put(PixelmonBlocks.fridge, "frost");
        blockMap.put(PixelmonBlocks.mower, "mow");
        blockMap.put(PixelmonBlocks.washing_machine, "wash");
        blockMap.put(PixelmonBlocks.fan, "fan");
        return blockMap;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static BiMap<String, Optional<ImmutableAttack>>getAttackMap() {

        if (attackMap.isEmpty()) {
            attackMap.put(PixelmonForms.NONE, AttackRegistry.THUNDER_SHOCK);
            attackMap.put("astral", AttackRegistry.CONFUSION);
            attackMap.put("heat", AttackRegistry.OVERHEAT);
            attackMap.put("frost", AttackRegistry.BLIZZARD);
            attackMap.put("mow", AttackRegistry.LEAF_STORM);
            attackMap.put("wash", AttackRegistry.HYDRO_PUMP);
            attackMap.put("fan", AttackRegistry.AIR_SLASH);
        }

        return attackMap;
    }
    /**
     * @author
     * @reason
     */
    @Overwrite
    private static boolean replaceMove(Pokemon pokemon, String from1, String to1) {
        String from = from1;
        if(from.contains("astral") && !from.equals("astral")){
            from = from.replace("astral", "");
        }

        String to = to1;
        if(to.contains("astral") && !to.equals("astral")){
            to = to.replace("astral", "");
        }

        int index = -1;
        Optional<ImmutableAttack> fromAttack = (Optional)getAttackMap().get(from);

        for(int i = 0; i < 4; ++i) {
            Attack attack = pokemon.getMoveset().get(i);
            if (attack != null && attack.isAttack(new Optional[]{fromAttack})) {
                index = i;
            }
        }

        if (index == -1) {
            return false;
        } else {
            pokemon.getMoveset().set(index, ((ImmutableAttack)((Optional)getAttackMap().get(to)).get()).ofMutable());
            return true;
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    @SubscribeEvent
    public static void onRotomChangeForm(PokeBallImpactEvent event){
        if (!event.getPokeBall().level.isClientSide && event.getBlockHit().isPresent() && !event.isEmptyPokeBall() && event.getPokeBall().getOwner() instanceof ServerPlayerEntity) {
            Species species = (Species)((OccupiedPokeBallEntity)event.getPokeBall()).getPokemon().orElse((Species)null);
            if (species != null && species.is(new RegistryValue[]{PixelmonSpecies.ROTOM})) {
                Block block = ((BlockState)event.getBlockHit().get()).getBlock();
                if (getBlockImpactMap().containsKey(block)) {
                    String form = (String)getBlockImpactMap().get(block);
                    PlayerPartyStorage party = StorageProxy.getParty((ServerPlayerEntity)event.getPokeBall().getOwner());
                    Pokemon pokemon = party.find(event.getPokeBall().getPokeUUID());

                    String pokemonForm = pokemon.getForm().getName();

                    if (pokemonForm.toLowerCase().contains("astral")) {
                        String currForm = form;
                        form = "astral" + currForm;
                    }

                    if (form.equalsIgnoreCase(pokemon.getForm().getName())) {
                        if(form.contains("astral")){
                            form = "astral";
                        } else {
                            form = PixelmonForms.NONE;
                        }
                    }

                    if (replaceMove(pokemon, pokemon.getForm().getName(), form)) {
                        pokemon.setForm(form);
                    } else {
                        LearnMoveController.sendLearnMove(Objects.requireNonNull(party.getPlayer()), pokemon.getUUID(), (ImmutableAttack)((Optional<?>)getAttackMap().get(form)).get());
                    }

                }
            }
        }
    }
}