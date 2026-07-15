package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.PokemonBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PokemonBase.class)
public abstract class PokemonBaseMixin {
    @Shadow
    public abstract boolean isPalette(String palette);
    /**
     * @author
     * @reason
     */
    @Overwrite
    public boolean isShiny() {
        return this.isPalette("shiny") || this.isPalette("shiny2");
    }
}
