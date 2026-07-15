package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBase;
import com.pixelmonmod.pixelmon.api.pokemon.species.palette.PaletteProperties;
import es.boffmedia.teras.Teras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;

@Mixin(Pokemon.class)
public abstract class PokemonMixin extends PokemonBase{
    @Shadow
    public abstract boolean setPalette(String palette);

    @Shadow public abstract PaletteProperties getPalette();

    /**
     * @author
     * @reason
     */
    @Overwrite
    public boolean setShiny(boolean shiny) {
        if (this.isShiny() && !shiny) {
            return this.setPalette(this.getGenderProperties().getDefaultPalette());
        } else {
            double random = new Random().nextDouble();
            if (random < 0.5 &&  this.hasPalette("shiny2")){
                Teras.getLogger().info("SHINY2");
                return this.setPalette("shiny2");
            }
            return shiny && this.setPalette("shiny");
        }
    }

}
