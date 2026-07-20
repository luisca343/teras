package es.boffmedia.teras.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips {@code teras.mixins.json}'s mixins unless Pixelmon is present — their targets are
 * {@code com.pixelmonmod.*} classes, and a missing target is a hard load error. {@link LoadingModList}
 * (not {@code ModList}) is used because mixins apply before {@code ModList} is populated.
 *
 * <p>Also registers {@link es.boffmedia.teras.integration.CreativeCoreCompat}, which keeps
 * CreativeCore from crashing the game when it loses a patch race against Pixelmon.
 */
public class TerasMixinPlugin implements IMixinConfigPlugin {

    private boolean pixelmonPresent;

    @Override
    public void onLoad(String mixinPackage) {
        pixelmonPresent = LoadingModList.get() != null
                && LoadingModList.get().getModFileById("pixelmon") != null;
        if (pixelmonPresent) {
            // By name: loading the handler here would pull it through the transformer far too early.
            // Every config is selected before any mixin applies, so this lands before CreativeCore's.
            Mixins.registerErrorHandlerClass("es.boffmedia.teras.integration.CreativeCoreCompat");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return pixelmonPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
