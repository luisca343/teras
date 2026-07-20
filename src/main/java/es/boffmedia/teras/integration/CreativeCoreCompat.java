package es.boffmedia.teras.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Downgrades the failure of CreativeCore's {@code ComponentSerialization} mixin from fatal to a
 * warning, so a pack running both CreativeCore and Pixelmon still boots.
 *
 * <p>Both mods patch the same vanilla codec method for overlapping reasons (legacy text-component
 * handling). Pixelmon rewrites it, so CreativeCore's injector no longer finds its target, and
 * because {@code creativecore.mixins.json} is {@code required} that miss aborts class load. Only
 * this one patch is skipped — Pixelmon's equivalent is already in place and the rest of CreativeCore
 * applies untouched.
 *
 * <p>Deliberately outside {@code es.boffmedia.teras.mixin}: Mixin throws {@code
 * IllegalClassLoadError} for any class loaded from a registered mixin package, and it swallows
 * throwables raised while instancing error handlers — a handler placed there would silently never
 * run.
 */
public final class CreativeCoreCompat implements IMixinErrorHandler {

    /** Not {@code Teras.LOGGER}: this runs mid-transformation, before the mod class should load. */
    private static final Logger LOGGER = LoggerFactory.getLogger("Teras");

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return suppress(null, mixin);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return suppress(targetClassName, mixin);
    }

    /** @return {@code WARN} to skip the offending mixin, or {@code null} to keep Mixin's verdict. */
    private static ErrorAction suppress(String targetClassName, IMixinInfo mixin) {
        if (mixin == null) {
            return null;
        }
        String mixinClass = mixin.getClassName();
        if (!isCreativeCore(mixin, mixinClass) || !touchesComponentSerialization(targetClassName, mixinClass)) {
            return null;
        }
        LOGGER.warn("Skipping CreativeCore mixin {} (target {}) — Pixelmon already rewrites that method.",
                mixinClass, targetClassName != null ? targetClassName : "unknown");
        return ErrorAction.WARN;
    }

    private static boolean isCreativeCore(IMixinInfo mixin, String mixinClass) {
        IMixinConfig config = mixin.getConfig();
        return contains(config != null ? config.getName() : null, "creativecore")
                || contains(mixinClass, "creativecore");
    }

    private static boolean touchesComponentSerialization(String targetClassName, String mixinClass) {
        return contains(targetClassName, "componentserialization")
                || contains(mixinClass, "componentserialization");
    }

    private static boolean contains(String haystack, String lowercaseNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lowercaseNeedle);
    }
}
