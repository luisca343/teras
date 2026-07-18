package es.boffmedia.teras.pixelmon;

import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.comm.packetHandlers.OpenScreenPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientChangeOpenPCPacket;
import com.pixelmonmod.pixelmon.enums.EnumGuiScreen;
import es.boffmedia.teras.Teras;
import net.minecraft.server.level.ServerPlayer;

/**
 * Opens a player's own Pokémon PC (the {@code openPC} SmartRotom query). Compiles against Pixelmon, so
 * it is only ever named behind a {@code ModList.isLoaded("pixelmon")} guard.
 *
 * <p>The {@code ClientChangeOpenPCPacket} that points the client's PC at the player must precede the
 * screen packet, or the screen opens on whatever storage was last viewed.</p>
 */
public final class PCOpener {
    private PCOpener() {}

    /** Opens the PC for {@code player}, returning {@code null} on success or a failure reason. */
    public static String open(ServerPlayer player) {
        if (es.boffmedia.teras.battle.tower.BattleTower.isActive(player)) {
            return "battle_tower_active";
        }
        try {
            NetworkHelper.sendPacket(new ClientChangeOpenPCPacket(player.getUUID()), player);
            OpenScreenPacket.open(player, EnumGuiScreen.PC);
            return null;
        } catch (Exception e) {
            Teras.LOGGER.error("Error opening the PC for {}", player.getGameProfile().getName(), e);
            return "pc_open_failed";
        }
    }
}
