package es.boffmedia.teras.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.entity.EntityDialogNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.client.PacketDialog;
import noppes.npcs.packets.client.PacketDialogDummy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Puts the real character in the dialogue window instead of the stand-in.
 *
 * <p>{@code PacketDialogDummy} carries a dialogue's whole text but no entity — only a name — so its
 * client handler builds a throwaway {@code EntityDialogNpc}, poses it off the <i>player</i>
 * ({@code EntityUtil.Copy}) and relies on {@code hideNPC} to keep that wrong face off the screen.
 * Teras sends its dungeon dialogues down that packet on purpose ({@code CnpcDialogMixin}), and the
 * character it is speaking for is standing right there in the world — so it is found by the one
 * thing the packet does carry, its name, and drawn instead.</p>
 *
 * <p>Nothing else changes: on a dialogue that really has no NPC behind it the search finds nothing
 * and the stand-in is used exactly as before, and one that asked to hide its NPC still hides it,
 * because {@code hideNPC} is left as the operator authored it rather than overridden here.</p>
 */
@Mixin(PacketDialogDummy.class)
public abstract class CnpcDialogPortraitMixin {

    @Shadow @Final private String name;

    @Redirect(method = "handle", require = 0,
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/packets/client/PacketDialog;"
                    + "openDialog(Lnoppes/npcs/controllers/data/Dialog;"
                    + "Lnoppes/npcs/entity/EntityNPCInterface;"
                    + "Lnet/minecraft/world/entity/player/Player;)V"))
    private void teras$drawTheSpeakerNotAStandIn(Dialog dialog, EntityNPCInterface standIn,
                                                 Player reader) {
        EntityNPCInterface speaker = teras$nearestNamed(reader, name);
        PacketDialog.openDialog(dialog, speaker == null ? standIn : speaker, reader);
    }

    private static EntityNPCInterface teras$nearestNamed(Player reader, String name) {
        // Earshot, not a constant field: a mixin's static initialiser has to be merged into the
        // target's, and this one target may not have a <clinit> to merge into.
        List<Entity> candidates = reader.level().getEntities(reader,
                reader.getBoundingBox().inflate(12.0),
                entity -> entity instanceof EntityNPCInterface
                        && !(entity instanceof EntityDialogNpc)
                        && entity.getName().getString().equals(name));
        EntityNPCInterface nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double distance = candidate.distanceToSqr(reader);
            if (distance < best) {
                best = distance;
                nearest = (EntityNPCInterface) candidate;
            }
        }
        return nearest;
    }
}
