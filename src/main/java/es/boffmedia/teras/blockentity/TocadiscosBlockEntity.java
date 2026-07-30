package es.boffmedia.teras.blockentity;

import es.boffmedia.teras.audio.DiscPlayback;
import es.boffmedia.teras.init.BlockEntityInit;
import es.boffmedia.teras.items.Disco;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The record player's state: which disc is in it, and whether it repeats.
 *
 * <p>Audio itself lives in {@link DiscPlayback}, keyed by world position — this holds no player and
 * no samples, which is what lets it be saved, loaded and unloaded like any other block entity while
 * a track is running.</p>
 */
public class TocadiscosBlockEntity extends BlockEntity {

    private ItemStack disc = ItemStack.EMPTY;
    private boolean loop = true;

    /** Last redstone level seen, so {@code neighborChanged} can tell an edge from a re-notify. */
    private boolean wasPowered;

    /**
     * Whether this jukebox is <b>meant</b> to be sounding, as opposed to whether audio is running
     * right now. Persisted, and the two come apart constantly: a chunk unload kills the audio
     * without anyone switching it off, and a running track belongs to the world position rather
     * than to this object. Without it a looping record in a bar goes quiet the first time nobody is
     * standing there and never comes back.
     */
    private boolean active;

    public TocadiscosBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityInit.TOCADISCOS.get(), pos, state);
    }

    public ItemStack getDisc() {
        return disc;
    }

    public boolean hasDisc() {
        return !disc.isEmpty();
    }

    public boolean isLooping() {
        return loop;
    }

    /** The track the inserted disc carries, or {@code null} when empty or blank. */
    public String track() {
        return disc.isEmpty() ? null : Disco.trackOf(disc);
    }

    public void setDisc(ItemStack stack) {
        disc = stack.copyWithCount(1);
        setChanged();
    }

    /** Takes the disc out, stopping playback. Returns what was inside. */
    public ItemStack removeDisc() {
        ItemStack removed = disc;
        disc = ItemStack.EMPTY;
        stop();
        setChanged();
        return removed;
    }

    public boolean wasPowered() {
        return wasPowered;
    }

    public void setWasPowered(boolean powered) {
        wasPowered = powered;
        setChanged();
    }

    public void setLooping(boolean looping) {
        loop = looping;
        setChanged();
        // Applied on the next lap rather than restarting: a loop toggled mid-song should not
        // restart the song.
    }

    /**
     * Starts the inserted disc.
     *
     * @return false when there is nothing to play or no voice server to play it through
     */
    public boolean play() {
        String track = track();
        if (track == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        active = true;
        setChanged();
        return DiscPlayback.start(serverLevel, worldPosition, track, DISTANCE, loop);
    }

    public void stop() {
        active = false;
        setChanged();
        stopAudio();
    }

    /** Silences the position without clearing {@link #active} — for going away, not switching off. */
    private void stopAudio() {
        if (level instanceof ServerLevel serverLevel) {
            DiscPlayback.stop(serverLevel, worldPosition);
        }
    }

    /**
     * Picks a record back up after a chunk load or a server restart.
     *
     * <p>{@code onLoad} rather than a ticker: this needs to happen once, when the block comes back,
     * and a block entity that ticks purely to notice it should be playing is a tick wasted on every
     * jukebox on the map.</p>
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (active && hasDisc() && level instanceof ServerLevel serverLevel) {
            String track = track();
            if (track != null) {
                DiscPlayback.start(serverLevel, worldPosition, track, DISTANCE, loop);
            }
        }
    }

    public boolean isPlaying() {
        return level instanceof ServerLevel serverLevel && DiscPlayback.isPlaying(serverLevel, worldPosition);
    }

    /**
     * A running track is owned by the position, not by this object, so it has to be stopped when
     * the block entity goes away — otherwise unloading the chunk leaves music playing in an
     * unloaded chunk forever.
     */
    @Override
    public void setRemoved() {
        // stopAudio, not stop: an unload must silence the position without recording that somebody
        // turned the jukebox off, or it would never resume when the chunk comes back.
        stopAudio();
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        disc = tag.contains("Disc")
                ? ItemStack.parse(registries, tag.getCompound("Disc")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        loop = !tag.contains("Loop") || tag.getBoolean("Loop");
        wasPowered = tag.getBoolean("Powered");
        active = tag.getBoolean("Active");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!disc.isEmpty()) {
            tag.put("Disc", disc.save(registries, new CompoundTag()));
        }
        tag.putBoolean("Loop", loop);
        tag.putBoolean("Powered", wasPowered);
        tag.putBoolean("Active", active);
    }

    /** Blocks the music carries. Matches the audio layer's own default. */
    private static final float DISTANCE = 24.0f;
}
