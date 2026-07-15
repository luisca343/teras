package es.boffmedia.teras.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.ClientProxy;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.montoyo.mcef.api.API;
import net.montoyo.mcef.api.IBrowser;

import java.awt.*;
import java.awt.event.MouseEvent;


@OnlyIn(Dist.CLIENT)
public class PantallaVideo extends Screen {

    private ClientProxy.PadData pad;
    IBrowser browser = null;
    String url;
    String urlVideo;

    private long initTime = System.currentTimeMillis();

    private static final String YT_REGEX1 = "^https?://(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_\\-]+)$";
    private static final String YT_REGEX2 = "^https?://(?:www\\.)?youtu\\.be/([a-zA-Z0-9_\\-]+)$";
    private static final String YT_REGEX3 = "^https?://(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_\\-]+)(\\?.+)?$";

    private int lastWidth = -1, lastHeight = -1;
    public PantallaVideo() {
        super(new StringTextComponent("forgecef.example.screen.title"));
       // urlToLoad = MCEF.HOME_PAGE;
    }

    public PantallaVideo(String url) {
        super(new StringTextComponent("forgecef.example.screen.title"));
        Teras.LOGGER.info("Constructor: " + url);
        this.url = url;
        this.urlVideo = url;
       // urlToLoad = (url == null) ? MCEF.HOME_PAGE : url;
    }

    @Override
    public void init() {
        super.init(); // narrator trigger lmao
        if(browser == null) {
            //Grab the API and make sure it isn't null.
            API api = Teras.getInstance().getAPI();
            if(api == null)
                return;

            browser = api.createBrowser("http://localhost:3000/video?url="+url, false);
            browser.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

        }else{
            browser.loadURL("http://localhost:3000/video?url="+url);
        }
/*
        if(url == null) {
            addButton(back = (new Button( 0, 0, 20, 20, new StringTextComponent("<"), (button -> this.legacyActionPerformed(0)))));
            addButton(fwd = (new Button( 20, 0, 20, 20, new StringTextComponent(">"),(button -> this.legacyActionPerformed(1)))));
            addButton(go = (new Button( width - 60, 0, 20, 20, new StringTextComponent("F5"), (button -> this.legacyActionPerformed(2)))));
            addButton(min = (new Button(width - 20, 0, 20, 20, new StringTextComponent("_"), (button -> this.legacyActionPerformed(3)))));
            addButton(vidMode = (new Button(width - 40, 0, 20, 20, new StringTextComponent("YT"), (button -> this.legacyActionPerformed(4)))));
            vidModeState = false;

            url = new TextFieldWidget(minecraft.font, 40, 0, width - 100, 20, new StringTextComponent(""));
            url.setMaxLength(65535);
            url.setValue(Teras.applyBlacklist(browser.getURL()));
        } else {
            addButton(back);
            addButton(fwd);
            addButton(go);
            addButton(min);
            addButton(vidMode);

            //Handle resizing
            vidMode.x = width - 40;
            go.x = width - 60;
            min.x = width - 20;

            String old = url.getValue();
            url = new TextFieldWidget(minecraft.font, 40, 0, width - 100, 20, new StringTextComponent(""));
            url.setMaxLength(65535);
            url.setValue(Teras.applyBlacklist(old));
        }*/

        this.initTime = System.currentTimeMillis();
    }


    @Override
    public void tick() {
        super.tick();
/*
        if (urlToLoad != null && browser != null) {
            browser.loadURL(urlToLoad);
            urlToLoad = null;
        }

        if (url != null) {
            if (url.isFocused()) {
                url.tick();
            } else {
                url.setCursorPosition(0);
            }
        }*/

        if (minecraft != null && browser != null && browser.isActivate()) {
            int curWidth = minecraft.getWindow().getWidth();
            //int curHeight = minecraft.getWindow().getHeight() - scaleY(20);
            int curHeight = minecraft.getWindow().getHeight();
            if (curHeight > 0 && curWidth > 0 && (lastWidth != curWidth || lastHeight != curHeight)) {
                browser.resize(curWidth, curHeight);
            }
        }

    }

    public int scaleY(int y) {
        assert minecraft != null;
        double sy = ((double) y) / ((double) height) * ((double) minecraft.getWindow().getHeight());
        return (int) sy;
    }

    public int scaleX(int x) {
        assert minecraft != null;
        double sx = ((double) x) / ((double) width) * ((double) minecraft.getWindow().getWidth());
        return (int) sx;
    }

    public void loadURL(String url) {
        /*if(browser == null)
            urlToLoad = Teras.SMARTROTOM_HOME;
        else
            urlToLoad = url;*/
    }

    public void preRender() {
        /*if(urlToLoad != null && browser != null) {
            urlToLoad = Teras.SMARTROTOM_HOME;
        }*/
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.preRender();
        //url.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
        if(browser != null) {
            GlStateManager._disableDepthTest();
            GlStateManager._enableTexture();
            browser.draw(matrices, .0d, height, width, 0); //Don't forget to flip Y axis.
            GlStateManager._enableDepthTest();
        }
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(null);
        browser.close();
    }

    @Override
    public boolean mouseDragged(double ox, double oy, int btn, double nx, double ny) {
        boolean consume = super.mouseDragged(ox, oy, btn, nx, ny);
        if (browser != null && !consume) {
            int sx = (int) (ox / (float) width * minecraft.getWindow().getWidth());
            //int sy = (int) ((oy - 20) / (float) height * minecraft.getWindow().getHeight());
            int sy = (int) (oy / (float) height * minecraft.getWindow().getHeight());
            int ex = (int) (ox / (float) width * minecraft.getWindow().getWidth());
            //int ey = (int) ((oy - 20) / (float) height * minecraft.getWindow().getHeight());
            int ey = (int) (oy / (float) height * minecraft.getWindow().getHeight());
            browser.injectMouseDrag(sx, sy, remapBtn(btn), ex, ey);
        }

        return consume;
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        if (browser != null && minecraft != null && activateBtn == -1) {
            int sx = (int) (x / (float) width * minecraft.getWindow().getWidth());
            //int sy = (int) ((y - 20) / (float) height * minecraft.getWindow().getHeight());
            int sy = (int) (y / (float) height * minecraft.getWindow().getHeight());
            browser.injectMouseMove(sx, sy, getMask(), y < 0);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        activateBtn = btn;
        if(btn == 1) return false;
        boolean consume = super.mouseClicked(x, y, btn);
        if (!consume && browser != null && minecraft != null) {
            int sx = (int) (x / (float) width * minecraft.getWindow().getWidth());
            //int sy = (int) ((y - 20) / (float) height * minecraft.getWindow().getHeight());
            int sy = (int) (y / (float) height * minecraft.getWindow().getHeight());
            browser.injectMouseButton(sx, sy, getMask(), remapBtn(btn), true, 1);
            return true;
        }

        return consume;
    }


    private int activateBtn = -1;

    @Override
    public boolean mouseReleased(double x, double y, int btn) {
        activateBtn = activateBtn == btn ? -1 : activateBtn;
        boolean consume = super.mouseReleased(x, y, btn);
        if (!consume && browser != null && minecraft != null) {
            int sx = (int) (x / (float) width * minecraft.getWindow().getWidth());
            //int sy = (int) (((y - 20) / (float) height) * minecraft.getWindow().getHeight());
            int sy = (int) ((y / (float) height) * minecraft.getWindow().getHeight());
            browser.injectMouseButton(sx, sy, getMask(), remapBtn(btn), false, 1);
            return true;
        }

        return consume;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double wheel) {
        boolean consume = super.mouseScrolled(x, y, wheel);
        if (!consume && browser != null && minecraft != null) {
            int sx = (int) (x / (float) width * minecraft.getWindow().getWidth());
            //int sy = (int) (((y - 20) / (float) height) * minecraft.getWindow().getHeight());
            int sy = (int) ((y / (float) height) * minecraft.getWindow().getHeight());
            browser.injectMouseWheel(sx, sy, getMask(), 1, ((int) wheel * 100));
            return true;
        }
        return consume;
    }

    @Override
    public boolean charTyped(char key, int mod) {
        boolean consume = enviarInterfaz(key) ? super.charTyped(key, mod) : false; // 257 335
        if (browser != null && (!consume || key == 256)) {
            browser.injectKeyTyped(key, key, getMask());
            browser.close();
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int key, int p_231046_2_, int p_231046_3_) {
        if(key == 256 && p_231046_3_ != 1){
            return false;
        }
        boolean consume = enviarInterfaz(key) ? super.keyPressed(key, p_231046_2_, p_231046_3_) : false;
        if (minecraft == null) return true;

        char c = (char) key;

        if (browser != null && (!consume || key == 256)) {
            browser.injectKeyPressedByKeyCode(key, c, getMask());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int p_223281_2_, int p_223281_3_) {
        boolean consume = enviarInterfaz(key) ? super.keyReleased(key, p_223281_2_, p_223281_3_) : false;
        char c = (char) key;
        if (browser != null && (!consume || key == 256)) {
            browser.injectKeyReleasedByKeyCode(key, c, getMask());
            return true;
        }
        return false;
    }

    //Called by ExampleMod when the current browser's URL changes.
    public void onUrlChanged(IBrowser b, String nurl) {
        /*if (b == browser && url != null) {
            url.setValue(nurl);
        }*/
    }

    //remap from GLFW to AWT's button ids
    private int remapBtn(int btn) {
        if (btn == 0) {
            btn = MouseEvent.BUTTON1;
        } else if (btn == 1) {
            btn = MouseEvent.BUTTON3;
        } else {
            btn = MouseEvent.BUTTON2;
        }
        return btn;
    }

    private static int getMask() {
        return (hasShiftDown() ? MouseEvent.SHIFT_DOWN_MASK : 0) |
                (hasAltDown() ? MouseEvent.ALT_DOWN_MASK : 0) |
                (hasControlDown() ? MouseEvent.CTRL_DOWN_MASK : 0);
    }


    //never used
    private final Point point = new Point();

    private Point transform2BrowserSize(double x, double y) {
        int sx = (int) (x / (float) width * minecraft.getWindow().getHeight());
        // 20 is the top search box's height
        //int sy = (int) ((y - 20) / (float) height * minecraft.getWindow().getHeight());
        int sy = (int) (y / (float) height * minecraft.getWindow().getHeight());
        point.setLocation(sx, sy);
        return point;
    }

    private boolean enviarInterfaz(int key) {
        switch (key){
            case 256: return true;
            default: return false;
        }
    }

}
