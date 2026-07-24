package es.boffmedia.teras.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips a mixin whose target mod is not installed — a missing target class is a hard load error,
 * and every mixin here patches an optional dependency. The owner is read off the target's package:
 * {@code com.pixelmonmod.*} needs Pixelmon, {@code noppes.*} needs CustomNPCs. {@link LoadingModList}
 * (not {@code ModList}) is used because mixins apply before {@code ModList} is populated.
 *
 * <p>Also registers {@link es.boffmedia.teras.integration.CreativeCoreCompat}, which keeps
 * CreativeCore from crashing the game when it loses a patch race against Pixelmon.
 */
public class TerasMixinPlugin implements IMixinConfigPlugin {

    private boolean pixelmonPresent;
    private boolean customNpcsPresent;

    @Override
    public void onLoad(String mixinPackage) {
        pixelmonPresent = loaded("pixelmon");
        customNpcsPresent = loaded("customnpcs");
        if (pixelmonPresent) {
            // By name: loading the handler here would pull it through the transformer far too early.
            // Every config is selected before any mixin applies, so this lands before CreativeCore's.
            Mixins.registerErrorHandlerClass("es.boffmedia.teras.integration.CreativeCoreCompat");
        }
    }

    private static boolean loaded(String modId) {
        return LoadingModList.get() != null && LoadingModList.get().getModFileById(modId) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return targetClassName.startsWith("noppes.") ? customNpcsPresent : pixelmonPresent;
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
