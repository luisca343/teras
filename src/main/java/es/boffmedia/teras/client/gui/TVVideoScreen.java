package es.boffmedia.teras.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.gui.components.CustomSlider;
import es.boffmedia.teras.net.video.UploadVideoUpdateMessage;
import es.boffmedia.teras.tileentity.FrameBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.ImageButton;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import es.boffmedia.teras.net.PacketHandler;

public class TVVideoScreen extends Screen {


    private static final ResourceLocation TEXTURE = new ResourceLocation(Teras.MOD_ID, "textures/gui/background.png");
    private static final ResourceLocation BUTTONS = new ResourceLocation(Teras.MOD_ID, "textures/gui/frame.png");

    private final TileEntity be;
    private String url;
    private int volume;


    // GUI
    private final int imageWidth = 256;
    private final int imageHeight = 256;
    private int leftPos;
    private int topPos;

    // Components useful for the GUI
    private TextFieldWidget urlBox;
    private TextFieldWidget canalBox;
    private CustomSlider volumeSlider;

    private int prevX;
    private int prevY;

    private TextFieldWidget xBox;
    private TextFieldWidget yBox;

    private boolean changed;

    private int posX = 0;
    private int posY = 0;

    private int canal;

    private boolean permisos;
    ClientPlayerEntity mcPlayer;

    public TVVideoScreen(TileEntity be, String url, int volume, int sizeX, int sizeY, int posX, int posY, int canal, boolean permisos) {
        super(new TranslationTextComponent("gui.frame.title"));

        this.be = be;
        this.url = url;
        this.volume = volume;
        this.prevX = sizeX;
        this.prevY = sizeY;
        this.posX = posX;
        this.posY = posY;
        this.canal = canal;
        this.permisos = permisos;
        mcPlayer = Minecraft.getInstance().player;

        Teras.LOGGER.info("TVVideoScreen: " + be + " " + url + " " + volume + " " + sizeX + " " + sizeY + " " + posX + " " + posY + " " + canal + " " + permisos);

    }

    @Override
    protected void init() {
        super.init();

        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;

        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);

        addButton(urlBox = new TextFieldWidget(font, leftPos + 10, topPos + 30, imageWidth - 26, 20, new StringTextComponent("")));
        // Set the text to the url
        urlBox.setMaxLength(32767);
        urlBox.setValue(url == null ? "" : url);

        // X dir button
        addButton(new Button(leftPos + 10, topPos + 75, (imageWidth - 24) / 2 +5, 20, new StringTextComponent(getStrPosX()), button -> {
            posX++;
            if(posX > 2) posX = 0;
            button.setMessage(new StringTextComponent(getStrPosX()));
        }));

        // Y dir button
        addButton(new Button(leftPos + 10 + (imageWidth - 24) / 2 +5, topPos + 75, (imageWidth - 24) / 2 -5, 20, new StringTextComponent(getStrPosY()), button -> {
            posY++;
            if(posY > 2) posY = 0;
            button.setMessage(new StringTextComponent(getStrPosY()));

        }));



        addButton(new ImageButton(
                leftPos + imageWidth - 50 - 96,
                topPos + 215,
                20,
                20,
                0,
                0,
                0,
                BUTTONS,
                256,
                256, button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, true, true, getValueX() ,getValueY(), posX, posY, getCanal()));
        }));

        /*
        // Play button
        addButton(new Button(leftPos + 10, topPos + 80, imageWidth - 24, 20, new TranslationTextComponent("gui.frame.play"), button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, true, true, getValueX() ,getValueY(), posX, posY));
        }));*/

        // new pause
        addButton(new ImageButton(
                leftPos + imageWidth - 50 - 64,
                topPos + 215,
                20,
                20,
                20,
                0,
                0,
                BUTTONS,
                256,
                256, button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, false, false, getValueX() ,getValueY(), posX, posY, getCanal()));
        }));

        // new stop

        addButton(new ImageButton(
                leftPos + imageWidth - 50 - 32,
                topPos + 215,
                20,
                20,
                40,
                0,
                0,
                BUTTONS,
                256,
                256, button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, false, true, getValueX() ,getValueY(), posX, posY, getCanal()));
        }));


        // Pause button
        /*
        addButton(new Button(leftPos + 10, topPos + 105, imageWidth - 24, 20, new TranslationTextComponent("gui.frame.pause"), button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, false, false, getValueX() ,getValueY(), posX, posY));
        }));*/

        // Stop button
        /*
        addButton(new Button(leftPos + 10, topPos + 130, imageWidth - 24, 20, new TranslationTextComponent("gui.frame.stop"), button -> {
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, volume, true, false, true, getValueX() ,getValueY(), posX, posY));
        }));*/


        //new save
        addButton(new ImageButton(
                leftPos + imageWidth - 50,
                topPos + 215,

                20,
                20,
                60,
                0,
                0,
                BUTTONS,
                256,
                256, button -> {

             if(!permisos) {
                 volume = 0;

             }else{
                 int tempVolume = volumeSlider.getValue();
                 this.volume = tempVolume;
                 ((FrameBlockEntity) be).setVolume(tempVolume);
             }

            String tempUrl = urlBox.getValue();
            this.url = tempUrl;

            // Cast the block entity to the correct type and set the volume

            changed = true;
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), tempUrl, volume, true, true, false, getValueX() ,getValueY(), posX, posY, getCanal()));
        }));


        // NUMERITOS
        xBox = new TextFieldWidget(font, leftPos + 10, topPos + 120, (imageWidth - 24)/2 - 5, 20, new StringTextComponent(""));
        xBox.setValue(String.valueOf(prevX));
        addButton(xBox);

        yBox = new TextFieldWidget(font, leftPos + 10 + (imageWidth - 24)/2 + 5, topPos + 120, (imageWidth - 24)/2 - 5, 20, new StringTextComponent(""));
        yBox.setValue(String.valueOf(prevY));
        addButton(yBox);


        if(permisos){
            // Volume slider
            addButton(volumeSlider = new CustomSlider(leftPos + 10, topPos + 145, imageWidth - 24, 20, new TranslationTextComponent("gui.frame.volume"), volume / 100f));



            addButton(canalBox = new TextFieldWidget(font, leftPos + 10, topPos + 185, imageWidth - 26, 20, new StringTextComponent("")));
            canalBox.setMaxLength(32767);
            canalBox.setValue(canal + "");
        }



        /*
        // Save button
        addButton(new Button(leftPos + 10, topPos + 220, imageWidth - 24, 20, new TranslationTextComponent("gui.frame.save"), button -> {
            int tempVolume = volumeSlider.getValue();
            String tempUrl = urlBox.getValue();

            this.url = tempUrl;
            this.volume = tempVolume;

            // Cast the block entity to the correct type and set the volume
            ((TVBlockEntity) be).setVolume(tempVolume);

            changed = true;
            Teras.LOGGER.info("ENVIANDO DATOS: " + tempUrl + " " + tempVolume + " " + getValueX() + " " + getValueY());
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), tempUrl, tempVolume, true, true, false, getValueX() ,getValueY(), posX, posY));
        }));*/

        // Cast the block entity to the correct type and set the volume
        ((FrameBlockEntity) be).setVolume(volume);
    }

    private int getCanal() {
        if(!permisos) return 0;
        String sCanal = canalBox.getValue();
        int canal;

        try{
            canal = Integer.parseInt(sCanal);
        }catch (NumberFormatException e){
            canal = 0;
        }

        return canal;
    }

    public String getStrPosX(){
        String strPosX = "";
        switch(posX){
            case 0:
                strPosX = "left";
                break;
            case 1:
                strPosX = "center";
                break;
            case 2:
                strPosX = "right";
                break;
        }
        return strPosX;
    }

    public String getStrPosY(){
        String strPosY = "";
        switch(posY){
            case 0:
                strPosY = "bottom";
                break;
            case 1:
                strPosY = "center";
                break;
            case 2:
                strPosY = "top";
                break;
        }
        return strPosY;
    }

    public int getValueX(){
        String sX = xBox.getValue();
        int x;

        try{
            x = Integer.parseInt(sX);
        }catch (NumberFormatException e){
            x = 1;
        }

        return x;
    }

    public int getValueY(){
        String sY = yBox.getValue();
        int y;

        try{
            y = Integer.parseInt(sY);
        }catch (NumberFormatException e){
            y = 1;
        }

        return y;
    }

    @Override
    public void render(MatrixStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
        renderBackground(pPoseStack);
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().getTextureManager().bind(TEXTURE);
        blit(pPoseStack, leftPos, topPos, 320, 320, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);

        font.draw(pPoseStack, new TranslationTextComponent("gui.frame.url_text"), width / 2f - font.width(new TranslationTextComponent("gui.frame.url_text")) / 2f, topPos + 16, 0xFFFFFF);
        font.draw(pPoseStack, new StringTextComponent("Posicion"), width / 2f - font.width(new StringTextComponent("Posicion")) / 2f, topPos + 60, 0xFFFFFF);
        font.draw(pPoseStack, new StringTextComponent("Ancho y alto"), width / 2f - font.width(new StringTextComponent("Ancho y alto")) / 2f, topPos + 105, 0xFFFFFF);
        if(permisos)font.draw(pPoseStack, new StringTextComponent("Canal"), width / 2f - font.width(new StringTextComponent("Canal")) / 2f, topPos + 170, 0xFFFFFF);
    }

    @Override
    public void removed() {
        if (!changed)
            PacketHandler.sendToServer(new UploadVideoUpdateMessage(be.getBlockPos(), url, -1, true, true, false, getValueX() ,getValueY(), posX, posY, getCanal()));
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}