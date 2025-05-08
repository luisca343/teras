package es.boffmedia.teras.util.objects.karts.editor;

import net.minecraft.entity.player.ServerPlayerEntity;
import es.boffmedia.teras.util.objects.karts.*;
import net.minecraft.util.math.BlockPos;

public class TrackEditorSession {
    private final ServerPlayerEntity player;
    private final RaceTrack track;
    private BlockPos lastSelectedPos;
    private EditorMode currentMode = EditorMode.CHECKPOINT;

    public TrackEditorSession(ServerPlayerEntity player, RaceTrack track) {
        this.player = player;
        this.track = track;
    }

    public enum EditorMode {
        CHECKPOINT,
        STARTING_POINT,
        POWERUP_POINT
    }

    public void setMode(EditorMode mode) {
        this.currentMode = mode;
    }

    public EditorMode getMode() {
        return currentMode;
    }

    public void setLastSelectedPos(BlockPos pos) {
        this.lastSelectedPos = pos;
    }

    public BlockPos getLastSelectedPos() {
        return lastSelectedPos;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public RaceTrack getTrack() {
        return track;
    }
}