package es.boffmedia.teras.client;


import es.boffmedia.teras.Teras;
import es.boffmedia.teras.client.gui.TVVideoScreen;
import es.boffmedia.teras.client.gui.VideoScreen;
import es.boffmedia.teras.tileentity.FrameBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
public class ClientHandler {

    public static void openVideo(String url, int volume) {
        Minecraft.getInstance().setScreen(new VideoScreen(url, volume));
    }

    public static void manageVideo(BlockPos pos, boolean playing, int tick, int sizeX, int sizeY, int posX, int posY, int canal, String url) {
        TileEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
        if (be instanceof FrameBlockEntity) {
            FrameBlockEntity frame = (FrameBlockEntity) be;
            frame.setPlaying(playing);
            frame.setTick(tick);
            frame.setSizeX(sizeX);
            frame.setSizeY(sizeY);
            frame.setPosX(posX);
            frame.setPosY(posY);
            frame.setCanal(canal);
            frame.setUrl(url);
            frame.updateAABB();



            if (frame.requestDisplay() != null) {
                if (playing)
                    frame.requestDisplay().resume(frame.getUrl(), frame.getVolume(), frame.minDistance, frame.maxDistance, frame.isPlaying(), frame.isLoop(), tick, sizeX, sizeY);
                else
                    frame.requestDisplay().pause(frame.getUrl(), frame.getVolume(), frame.minDistance, frame.maxDistance, frame.isPlaying(), frame.isLoop(), tick, sizeX, sizeY);
            }
        }
    }

    public static void openVideoGUI(BlockPos pos, String url, int tick, int volume, boolean loop, int sizeX, int sizeY, int posX, int posY, int canal, boolean permisos) {
        Teras.LOGGER.info("ClientHandler.openVideoGUI: " + pos + " " + url + " " + tick + " " + volume + " " + loop + " " + sizeX + " " + sizeY + " " + posX + " " + posY + " " + canal + " " + permisos);
        TileEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
        if (be instanceof FrameBlockEntity) {
            FrameBlockEntity frame = (FrameBlockEntity) be;
            frame.setUrl(url);
            frame.setTick(tick);
            frame.setVolume(volume);
            frame.setLoop(loop);
            Minecraft.getInstance().setScreen(new TVVideoScreen(be, url, volume, sizeX, sizeY, posX, posY, canal, permisos));
        }
    }

}