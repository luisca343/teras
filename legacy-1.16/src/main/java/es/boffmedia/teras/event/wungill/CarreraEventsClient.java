package es.boffmedia.teras.event.wungill;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import es.boffmedia.teras.Teras;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class CarreraEventsClient {
    public static int posicion = 0;
    private static ResourceLocation imagen;

    public static void actualizarPosicion(int pos){
        posicion = pos;
        imagen = new ResourceLocation(Teras.MOD_ID, "textures/posiciones/" + pos + ".png");
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderGameOverlay(RenderGameOverlayEvent.Post event)
    {
        if(event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if(posicion == 0 ) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        Minecraft.getInstance().getTextureManager().bind(imagen);
        int hei = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int wi = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        Minecraft.getInstance().gui.blit(event.getMatrixStack(), -64, (int) hei - 200, 0, 0, 256, 256);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    public static void scaledBlit(MatrixStack stack, int dw, int dh, int w, int h, int iw, int ih){
        Minecraft.getInstance().gui.blit(stack, escalarX(dw), escalarY(dh), escalarX(w), escalarY(h), escalarX(iw), escalarY(ih));
    }

    public static int escalarX(int n){
        return n*Minecraft.getInstance().getWindow().getWidth()/1920;
    }

    public static int escalarY(int n){
        return n*Minecraft.getInstance().getWindow().getHeight()/1080;
    }
}
