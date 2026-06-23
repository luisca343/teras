package es.boffmedia.teras.tileentity;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.blocks.TVBlock;
import es.boffmedia.teras.init.TileEntityInit;
import es.boffmedia.teras.net.Messages;
import es.boffmedia.teras.net.video.FrameVideoMessage;
import es.boffmedia.teras.net.video.OpenVideoManagerScreen;
import es.boffmedia.teras.util.math.CreateFrameBox;
import es.boffmedia.teras.util.cache.TextureCache;
import es.boffmedia.teras.util.displayers.IDisplay;
import es.boffmedia.teras.util.math.AlignedBox;
import es.boffmedia.teras.util.math.vector.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.server.permission.PermissionAPI;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;
public class FrameBlockEntity extends TileEntity implements ITickableTileEntity {

    private String url = "";
    private boolean playing = true;
    private int tick = 0;

    private float volume = 1.0F;

    public float minDistance = 5;
    public float maxDistance = 20;

    private boolean loop = true;

    private UUID playerUsing;

    @OnlyIn(Dist.CLIENT)
    public IDisplay display;

    @OnlyIn(Dist.CLIENT)
    public TextureCache cache;


    public FrameBlockEntity() {
        super(TileEntityInit.FRAME_TE.get());
    }


    public FrameBlockEntity(TileEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    public int sizeX = 1;
    public int sizeY = 1;

    public int posX = 0;
    public int posY = 0;

    public int canal = 0;

    public AxisAlignedBB AABB = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    @OnlyIn(Dist.CLIENT)
    public boolean isURLEmpty() {
        return url.isEmpty();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AABB;
    }

    public void updateAABB() {
        try{
            BlockPos pos = getBlockPos();
            AlignedBox box = new AlignedBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());

            Direction d = getBlockState().getValue(TVBlock.FACING);
            AlignedBox boxExtra = CreateFrameBox.getBox(box, d, sizeX, sizeY, posX, posY);

            AlignedBox boxFinal = getAlignedBox(box, boxExtra);


            Teras.LOGGER.info("box:" + box);
            Teras.LOGGER.info("boxExtra:" + boxExtra);
            Teras.LOGGER.info("boxFinal:" + boxFinal);

            AABB = boxFinal.getBB();

        }catch (Exception e) {
            Teras.LOGGER.warn("ERROR: " + e);
        }
    }

    @NotNull
    private AlignedBox getAlignedBox(AlignedBox box, AlignedBox boxExtra) {
        Direction d = getBlockState().getValue(TVBlock.FACING);
        float x1, y1, z1, x2, y2, z2;
        if(d.equals(Direction.NORTH) || d.equals(Direction.SOUTH)) {
            x1 = box.minX + boxExtra.maxX;
            y1 = box.minY + boxExtra.maxY;
            z1 = box.minZ + boxExtra.maxZ;

            x2 = box.maxX + Math.abs(boxExtra.minX);
            y2 = box.maxY + Math.abs(boxExtra.minY);
            z2 = box.maxZ + Math.abs(boxExtra.minZ);

        }else{

            x1 = box.minX + boxExtra.minX;
            y1 = box.minY + boxExtra.minY;
            z1 = box.minZ + boxExtra.minZ;

            x2 = box.maxX + boxExtra.maxX;
            y2 = box.maxY + boxExtra.maxY;
            z2 = box.maxZ + boxExtra.maxZ;
        }


        /*
        float x1 = (Math.abs(box.minX) + Math.abs(boxExtra.minX)) * Math.signum(box.minX);
        float y1 = (Math.abs(box.minY) + Math.abs(boxExtra.minY)) * Math.signum(box.minY);
        float z1 = (Math.abs(box.minZ) + Math.abs(boxExtra.minZ)) * Math.signum(box.minZ);

        float x2 = (Math.abs(box.maxX) + Math.abs(boxExtra.maxX)) * Math.signum(box.maxX);
        float y2 = (Math.abs(box.maxY) + Math.abs(boxExtra.maxY)) * Math.signum(box.maxY);
        float z2 = (Math.abs(box.maxZ) + Math.abs(boxExtra.maxZ)) * Math.signum(box.maxZ);
*/

        //AlignedBox boxFinal = new AlignedBox(box.minX + boxExtra.minX, box.minY + boxExtra.minY, box.minZ + boxExtra.minZ, box.minX + boxExtra.maxX, box.minY + boxExtra.maxY, box.minZ + boxExtra.maxZ);
        AlignedBox boxFinal = new AlignedBox(x1, y1, z1, x2, y2, z2);
        return boxFinal;
    }

    @OnlyIn(Dist.CLIENT)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public void setVolume(int volume) {
        this.volume = volume / 100F;
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public float getVolume() {
        return volume;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isLoop() {
        return loop;
    }

    public IDisplay requestDisplay() {
        String url = getUrl();
        if (cache == null || !cache.url.equals(url)) {
            cache = TextureCache.get(url);
            if (display != null)
                display.release();
            display = null;
        }
        if (!cache.isVideo() && (!cache.ready() || cache.getError() != null))
            return null;
        if (display != null)
            return display;
        return display = cache.createDisplay(new Vec3d(worldPosition), url, volume, minDistance, maxDistance, loop, playing);
    }

    public void tryOpen(World level, BlockPos blockPos, PlayerEntity player) {
        // If none is using the block, open the GUI
        if (playerUsing == null) {
            setBeingUsed(player.getUUID());
            openVideoManagerGUI(blockPos, player);
            return;
        }

        // If the player that use the block is connected, don't open the GUI
        for (PlayerEntity p : level.players())
            if (p.getUUID() == playerUsing)
                return;

        // Open the GUI
        openVideoManagerGUI(blockPos, player);
    }

    public void openVideoManagerGUI(BlockPos blockPos, PlayerEntity player) {
        setBeingUsed(player.getUUID());
        Teras.LOGGER.info("Comprobando permisos de " + player.getUUID());
        boolean permisos = PermissionAPI.hasPermission(player, "teras.frames.video");
        Teras.LOGGER.info("Permisos de " + player.getUUID() + ": " + permisos);
        Messages.sendTo(new OpenVideoManagerScreen(blockPos, url, tick, (int) (volume * 100), loop, sizeX, sizeY, posX, posY, canal, permisos), player);
    }

    public void setBeingUsed(UUID player) {
        this.playerUsing = player;
        setChanged();
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(getBlockPos(), 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.handleUpdateTag(getBlockState(), pkt.getTag());
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT nbt) {
        loadFromNBT(nbt);
        this.level.blockEntityChanged(this.worldPosition, this);
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return this.save(new CompoundNBT());
    }

    @Override
    public void setRemoved() {
        if (isClient() && display != null)
            display.release();
    }

    @Override
    public void onChunkUnloaded() {
        if (isClient() && display != null)
            display.release();
    }

    public boolean isClient() {
        return this.level != null && this.level.isClientSide;
    }

    @Override
    public CompoundNBT save(CompoundNBT pTag) {
        super.save(pTag);

        pTag.putString("url", url == null ? "" : url);
        pTag.putUUID("beingUsed", playerUsing == null ? new UUID(0, 0) : playerUsing);
        pTag.putBoolean("playing", playing);
        pTag.putInt("tick", tick);
        pTag.putFloat("volume", volume);
        pTag.putInt("sizeX", sizeX);
        pTag.putInt("sizeY", sizeY);
        pTag.putInt("posX", posX);
        pTag.putInt("posY", posY);
        pTag.putInt("canal", canal);
        return pTag;
    }



    @Override
    public void load(BlockState state, CompoundNBT pTag) {
        super.load(state, pTag);

        loadFromNBT(pTag);
    }

    private void loadFromNBT(CompoundNBT nbt) {
        url = nbt.getString("url");
        playerUsing = nbt.getUUID("beingUsed");
        playing = nbt.getBoolean("playing");
        tick = nbt.getInt("tick");
        volume = nbt.getFloat("volume");
        sizeX = nbt.getInt("sizeX");
        sizeY = nbt.getInt("sizeY");
        posX = nbt.getInt("posX");
        posY = nbt.getInt("posY");
        canal = nbt.getInt("canal");
        updateAABB();
    }

    public void notifyPlayer() {
        Messages.sendToClient(new FrameVideoMessage(worldPosition, playing, tick, sizeX, sizeY, posX, posY, canal, url), level, worldPosition);
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public void setSizeX(int sizeX) {
        if(sizeX > 0 && sizeX <= 10) {
            this.sizeX = sizeX;
        }

        if(sizeX > 10) {
            this.sizeX = 10;
        }

        if(sizeX < 0) {
            this.sizeX = 1;
        }
    }

    public void setSizeY(int sizeY) {
        if(sizeY > 0 && sizeY <= 10) {
            this.sizeY = sizeY;
        }

        if(sizeY > 10) {
            this.sizeY = 10;
        }

        if(sizeY < 0) {
            this.sizeY = 1;
        }
    }


    public void setPosX(int posX) {
        this.posX = posX;
    }

    public void setPosY(int posY) {
        this.posY = posY;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public AlignedBox getBox() {
        Direction direction = getBlockState().getValue(TVBlock.FACING);
        AlignedBox box = TVBlock.box(direction);

        /*

        Axis one = facing.one();
        Axis two = facing.two();

        if (facing.axis != Axis.Z) {
            one = facing.two();
            two = facing.one();
        }

        box.setMin(one, 0);
        box.setMax(one, getSizeX());

        box.setMin(two, 0);
        box.setMax(two, getSizeY());*/
        return box;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getTick() {
        return tick;
    }

    public void setTick(int tick) {
        this.tick = tick;
    }


    public void setCanal(int canal) {
        this.canal = canal;
    }

    @Override
    public void tick() {
        FrameBlockEntity be = this;
        if (level.isClientSide) {
            IDisplay display = be.requestDisplay();
            if (display != null)
                display.tick(be.url, be.volume, be.minDistance, be.maxDistance, be.playing, be.loop, be.tick);
        }
        if (be.playing)
            be.tick++;
    }

    public void broadcastVideo(String url) {
        Teras.LOGGER.info("Broadcasting video: " + url + " to " + worldPosition);
        this.url = url;
        this.tick = 0;
        this.playing = true;
        Messages.sendToClient(new FrameVideoMessage(worldPosition, true, tick, sizeX, sizeY, posX, posY, canal, url), level, worldPosition);
    }

    public int getCanal() {
        return canal;
    }
}