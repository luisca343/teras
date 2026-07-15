/*package es.boffmedia.teras.tileentity;

import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import es.boffmedia.teras.init.TileEntityInit;
import es.boffmedia.teras.util.music.AudioManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;


public class TocadiscosTE extends TileEntity {
    String disco;
    private AudioPlayer player;


    public TocadiscosTE() {
        super(TileEntityInit.TOCADISCOS.get());
    }

    public TocadiscosTE(Direction facing) {
        super(TileEntityInit.TOCADISCOS.get());
    }


    public String getDisco() {
        return disco;
    }

    public void setDisco(String nombre) {
        if(this.level.isClientSide()){
            return;
        }

        disco = nombre;
        AudioManager manager = new AudioManager();
        player = manager.getPlayer(this.level, this.worldPosition, nombre);
        if(player == null){
            return;
        }

        player.startPlaying();
    }

    public void stopDisco() {
        if(player != null) player.stopPlaying();
    }

    public boolean hasDisco() {
        return disco != null;
    }


}
*/