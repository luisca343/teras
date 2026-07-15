package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.pokemon.stats.extraStats.ShearableStats;
import com.pixelmonmod.pixelmon.api.util.helpers.RandomHelper;
import com.pixelmonmod.pixelmon.entities.pixelmon.AbstractBaseEntity;
import com.pixelmonmod.pixelmon.entities.pixelmon.interactions.custom.PixelmonInteraction;
import com.pixelmonmod.pixelmon.entities.pixelmon.interactions.custom.ShearInteraction;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.IItemProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Mixin(ShearInteraction.class)
public class ShearInteractionMixin  extends PixelmonInteraction {

    @Mutable
    @Final
    @Shadow
    public static Map<String, Block> WOOLS;
    @Mutable
    @Final
    @Shadow
    public static Map<DyeColor, String> PALETTES;

    public ShearInteractionMixin(int maxInteractions) {
        super(maxInteractions);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void overwriteWools(CallbackInfo ci) {
        WOOLS = new HashMap<String, Block>() {
            {
                this.put("white", Blocks.WHITE_WOOL);
                this.put("black", Blocks.BLACK_WOOL);
                this.put("blue", Blocks.BLUE_WOOL);
                this.put("brown", Blocks.BROWN_WOOL);
                this.put("cyan", Blocks.CYAN_WOOL);
                this.put("gray", Blocks.GRAY_WOOL);
                this.put("green", Blocks.GREEN_WOOL);
                this.put("lightgray", Blocks.LIGHT_GRAY_WOOL);
                this.put("lightblue", Blocks.LIGHT_BLUE_WOOL);
                this.put("lime", Blocks.LIME_WOOL);
                this.put("magenta", Blocks.MAGENTA_WOOL);
                this.put("orange", Blocks.ORANGE_WOOL);
                this.put("pink", Blocks.PINK_WOOL);
                this.put("purple", Blocks.PURPLE_WOOL);
                this.put("red", Blocks.RED_WOOL);
                this.put("yellow", Blocks.YELLOW_WOOL);
                this.put("astral", Blocks.PURPLE_WOOL);
            }
        };
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void overwritePalettes(CallbackInfo ci) {
        PALETTES = new HashMap<DyeColor, String>() {
            {
                this.put(DyeColor.WHITE, "white");
                this.put(DyeColor.BLACK, "black");
                this.put(DyeColor.BLUE, "blue");
                this.put(DyeColor.BROWN, "brown");
                this.put(DyeColor.CYAN, "cyan");
                this.put(DyeColor.GRAY, "gray");
                this.put(DyeColor.GREEN, "green");
                this.put(DyeColor.LIGHT_GRAY, "lightgray");
                this.put(DyeColor.LIGHT_BLUE, "lightblue");
                this.put(DyeColor.LIME, "lime");
                this.put(DyeColor.MAGENTA, "magenta");
                this.put(DyeColor.ORANGE, "orange");
                this.put(DyeColor.PINK, "pink");
                this.put(DyeColor.PURPLE, "purple");
                this.put(DyeColor.RED, "red");
                this.put(DyeColor.YELLOW, "yellow");
            }
        };
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public boolean processInteract(AbstractBaseEntity pixelmon, PlayerEntity player, Hand hand, ItemStack stack) {
        String pokemonForm = pixelmon.getForm().getName();
        boolean astral = pokemonForm.contains("astral");

        String shorn = astral ? "astralshorn" : "shorn";
        String palette = astral ? "astral" : pixelmon.getPalette().getName();


        if (!pixelmon.getPokemon().getForm().isForm(new String[]{shorn}) && pixelmon.getPokemon().getSpecies().hasForm(shorn) && stack != null && !stack.isEmpty()) {
            if (stack.getItem() == Items.SHEARS) {
                player.drop(new ItemStack((IItemProvider)WOOLS.getOrDefault(palette, Blocks.WHITE_WOOL), RandomHelper.getRandomNumberBetween(6, 8)), false);
                pixelmon.getPokemon().setForm(shorn);
                player.getCooldowns().addCooldown(stack.getItem(), 10);
                ShearableStats shearableStats = (ShearableStats)pixelmon.getPokemon().getExtraStats();
                shearableStats.growthStage = 10;
                return super.processInteract(pixelmon, player, hand, stack);
            }

            if (stack.getItem() instanceof DyeItem) {
                DyeColor color = ((DyeItem)stack.getItem()).getDyeColor();
                String newColor = color.name().toLowerCase(Locale.ROOT);
                if (!newColor.equalsIgnoreCase(palette) && !pixelmon.getPalette().is("shiny")) {
                    player.getItemInHand(hand).shrink(1);
                    player.getCooldowns().addCooldown(stack.getItem(), 10);
                    pixelmon.getPokemon().setPalette((String)PALETTES.getOrDefault(color, pixelmon.getGenderProperties().getDefaultPalette().getName()));
                }

                return true;
            }
        }

        return false;
    }

}