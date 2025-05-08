package es.boffmedia.teras.util.media;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.TerasVoicechatPlugin;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.apache.commons.io.IOUtils;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AudioManager {
    public final Map<UUID, AudioPlayer> players = new ConcurrentHashMap<>();

    public static boolean exists(String nombre){
        return getFile(nombre, "wav").toFile().exists();
    }

    public static Path getFile(String nombre, String extension){
        Path base = Paths.get("musica");
        if(!base.toFile().exists()){
            base.toFile().mkdir();
        }

        return getFile(nombre+ "." + extension);

    }


    public static Path getFile(String nombre){
        Path base = Paths.get("musica");
        if(!base.toFile().exists()){
            base.toFile().mkdir();
        }

        return base.resolve(nombre);

    }


    public AudioPlayer getPlayer(World level, BlockPos pos, String nombre) {
        VoicechatServerApi api = TerasVoicechatPlugin.SERVER_API;
        UUID channelID = UUID.randomUUID();
        Path file = getFile(nombre, "wav");
        LocationalAudioChannel channel = api.createLocationalAudioChannel(channelID, api.fromServerLevel(level), api.createPosition(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
        channel.setCategory(TerasVoicechatPlugin.MUSIC_DISC_CATEGORY);
        api.getPlayersInRange(api.fromServerLevel(level), channel.getLocation(), api.getBroadcastRange(), serverPlayer -> {
            VoicechatConnection connection = api.getConnectionOf(serverPlayer);
            if (connection != null) {
                return connection.isDisabled();
            }
            return true;
        }).stream().map(Player::getPlayer).map(ServerPlayerEntity.class::cast).forEach(player -> {
            player.displayClientMessage(new StringTextComponent("You need to enable voice chat to hear this music disc"), true);
        });

        try {
            AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), readSound(file));
            players.put(channelID, player);
            return player;
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void play(ServerWorld level, Path file){
        VoicechatServerApi api = TerasVoicechatPlugin.SERVER_API;
        UUID channelID = UUID.randomUUID();
        LocationalAudioChannel channel = api.createLocationalAudioChannel(channelID, api.fromServerLevel(level), api.createPosition(0 + 0.5D, 64 + 0.5D, 0 + 0.5D));

        api.getPlayersInRange(api.fromServerLevel(level), channel.getLocation(), api.getBroadcastRange(), serverPlayer -> {
            VoicechatConnection connection = api.getConnectionOf(serverPlayer);
            if (connection != null) {
                return connection.isDisabled();
            }
            return true;
        }).stream().map(Player::getPlayer).map(ServerPlayerEntity.class::cast).forEach(player -> {
            player.displayClientMessage(new StringTextComponent("You need to enable voice chat to hear this music disc"), true);
        });

        try {
            AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), readSound(file));
            players.put(channelID, player);
            player.startPlaying();
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void reproducirJugador(ServerPlayerEntity player, String file) {
        VoicechatServerApi api = TerasVoicechatPlugin.SERVER_API;
        UUID channelID = UUID.randomUUID();
        VoicechatConnection con = api.getConnectionOf(player.getUUID());
        if (con == null) {
            player.sendMessage(new StringTextComponent("No estás conectado al servidor de voz"), UUID.randomUUID());
            return;
        }
        
        StaticAudioChannel chan = api.createStaticAudioChannel(UUID.randomUUID(), api.fromServerLevel(player.level), con);

        chan.setCategory(TerasVoicechatPlugin.MUSIC_CATEGORY);
        try {
            player.sendMessage(new StringTextComponent("Reproduciendo solo para ti..."), UUID.randomUUID());
            AudioPlayer audioPlayer = api.createAudioPlayer(chan, api.createEncoder(), readSound(getFile(file, "wav")));

            players.put(channelID, audioPlayer);
            audioPlayer.startPlaying();

        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getAudioPlayer(ServerPlayerEntity player, String file) throws UnsupportedAudioFileException, IOException {
        VoicechatServerApi api = TerasVoicechatPlugin.SERVER_API;
        VoicechatConnection con = api.getConnectionOf(player.getUUID());
        StaticAudioChannel chan = api.createStaticAudioChannel(UUID.randomUUID(), api.fromServerLevel(player.level), con);


        AudioPlayer audioPlayer = api.createAudioPlayer(chan, api.createEncoder(), readSound(getFile(file, "wav")));

    }

    public static short[] readSound(Path file) throws UnsupportedAudioFileException, IOException {
        AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile());
        AudioInputStream converted = AudioSystem.getAudioInputStream(AudioConverter.FORMAT, in);



        return TerasVoicechatPlugin.SERVER_API.getAudioConverter().bytesToShorts(IOUtils.toByteArray(converted));
    }


    public void playMp3(ServerPlayerEntity player){
        try {
            Teras.LOGGER.info("RECUPERANDO AUDIO");
            short[] audio = AudioConverter.convertMp3v2(getFile("test", "mp3"));
            Teras.LOGGER.info("AUDIO RECIBIDO");


            AudioPlayer audioPlayer = TerasVoicechatPlugin.SERVER_API.createAudioPlayer(TerasVoicechatPlugin.SERVER_API.createEntityAudioChannel(UUID.randomUUID(), TerasVoicechatPlugin.SERVER_API.fromServerPlayer(player)), TerasVoicechatPlugin.SERVER_API.createEncoder(), audio);
            Teras.LOGGER.info("AUDIO PLAYER CREADO");
            audioPlayer.startPlaying();
            Teras.LOGGER.info("AUDIO PLAYER INICIADO");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedAudioFileException e) {
            throw new RuntimeException(e);
        }

        /*
        try {

            AudioPlayer audioPlayer = TerasVoicechatPlugin.SERVER_API.createAudioPlayer(TerasVoicechatPlugin.SERVER_API.createStaticAudioChannel(UUID.randomUUID(), TerasVoicechatPlugin.SERVER_API.fromServerLevel(player.level), TerasVoicechatPlugin.SERVER_API.getConnectionOf(player.getUUID())), TerasVoicechatPlugin.SERVER_API.createEncoder(), readSound(getFile("test", "wav")));
            audioPlayer.startPlaying();


        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

}
