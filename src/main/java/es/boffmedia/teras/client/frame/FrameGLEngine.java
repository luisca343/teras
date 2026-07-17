package es.boffmedia.teras.client.frame;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.watermedia.api.media.engines.GLEngine;

import java.util.concurrent.Executor;

/**
 * Bridges WaterMedia 3.x's {@link GLEngine} onto Minecraft's GL. The engine is engine-agnostic:
 * it does no GL itself, it calls the function hooks we hand it, and it defers any actual GL to the
 * render thread through the {@link Executor}. Routing every call through {@link GlStateManager}
 * (not raw {@code GL11}) keeps Minecraft's own GL-state cache in sync — WaterMedia warns that
 * bypassing it corrupts the {@code GlStateManager}.
 *
 * <p>The executor turns each decode-thread upload into a {@code recordRenderCall}, run on the render
 * thread before the next frame; {@code renderThread} must therefore be the real render thread, so
 * this is only ever called from {@link FrameMedia} while it is on it.</p>
 */
@OnlyIn(Dist.CLIENT)
final class FrameGLEngine {
    private FrameGLEngine() {}

    static GLEngine create(Thread renderThread, Executor renderThreadEx) {
        return new GLEngine.Builder(renderThread, renderThreadEx)
                .setGenTexture(GlStateManager::_genTexture)
                .setDelTexture(GlStateManager::_deleteTexture)
                .setActiveTexture(GlStateManager::_activeTexture)
                .setBindVertexArray(GlStateManager::_glBindVertexArray)
                // BindConsumer is (target, id); GlStateManager binds GL_TEXTURE_2D implicitly, which is
                // all WaterMedia uploads use — passing the id keeps the state cache correct.
                .setBindTexture((target, id) -> GlStateManager._bindTexture(id))
                .setTexParameter((target, pname, param) -> GlStateManager._texParameter(target, pname, param))
                .setPixelStore((pname, value) -> GlStateManager._pixelStore(pname, value))
                .setBindFrameBuffer((target, fb) -> GlStateManager._glBindFramebuffer(target, fb))
                .setBindBuffer((target, buffer) -> GlStateManager._glBindBuffer(target, buffer))
                .build();
    }

    /** Executor that runs {@code task} on the render thread before the next frame. */
    static Executor renderThreadExecutor() {
        return task -> RenderSystem.recordRenderCall(task::run);
    }
}
