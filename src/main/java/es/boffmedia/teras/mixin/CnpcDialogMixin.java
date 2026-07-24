package es.boffmedia.teras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import es.boffmedia.teras.dungeon.encounter.CnpcBridge;
import es.boffmedia.teras.dungeon.run.DungeonNpcs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.entity.EntityNPCInterface;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Lets an authored CustomNPCs dialogue quote the dungeon's live numbers — the coin price, the price
 * in hearts, what the party's purse holds, what it owes — by filling {@code %token%}s in the copy of
 * the dialogue that is about to be sent to one player.
 *
 * <p><b>Why a mixin at all.</b> CustomNPCs has no text substitution of its own in this build and no
 * usable hook: {@code EventHooks.onNPCDialog} hands its event to that NPC's own script engine and
 * never posts it to a bus, so nothing outside the mod can see a dialogue opening. What it does have
 * is {@code openDialog}'s {@code dialog.copy(player)} — a throwaway per-player render of the stored
 * dialogue, made one line before it is serialised into the packet. That copy is the only correct
 * place to write a price into: the operator's dialogue is never touched, and two parties on two
 * floors read two different offers out of the same authored sentence.</p>
 *
 * <p>Optional by design — {@link TerasMixinPlugin} skips it without CustomNPCs, and neither injector
 * is {@code require}d, so a CustomNPCs build that moves either call site costs the substitution and
 * nothing else. The failure is visible in-game as a literal {@code %monedas%} in the dialogue.</p>
 */
@Mixin(NoppesUtilServer.class)
public class CnpcDialogMixin {

    /**
     * Makes the dialogue's <i>text</i> cross the wire, which for a saved dialogue it otherwise never
     * does — and without which the substitution above is invisible.
     *
     * <p>{@code openDialog} picks one of two packets:</p>
     * <ul>
     *   <li>{@code PacketDialog(npcId, dialogId)} — <b>two ints</b>. The client renders from its own
     *       {@code DialogController.dialogs} cache, so a server-side edit reaches nobody.</li>
     *   <li>{@code PacketDialogDummy(name, nbt)} — the whole dialogue, serialised. Taken when the
     *       NPC is an {@code EntityDialogNpc} or <b>the stored dialogue's id is negative</b>.</li>
     * </ul>
     *
     * <p>So the id of the stored dialogue is reported as {@code -1} for the one read that chooses
     * between them. Everything downstream is unaffected: {@code PlayerData.dialogId} was already set
     * from the <i>copy</i> two instructions earlier, {@code Dialog.save} still writes the real
     * {@code DialogId} into the NBT, and the client echoes that real id back in
     * {@code SPacketDialogSelected} — which the server then resolves against its own stored dialogue,
     * so an option still runs the command the operator gave it.</p>
     *
     * <p>The guard is identity, not an ordinal: every other read of {@code Dialog.id} in this method
     * is on the copy, and only the branch that picks the packet reads the original. A future build
     * that moves the read cannot make this fire somewhere it corrupts the id.</p>
     *
     * <p>That path also forces {@code hideNPC} on, because the client rebuilds a stand-in NPC rather
     * than using the real one — see {@link #teras$keepTheAuthoredPortrait} and
     * {@code CnpcDialogPortraitMixin}, which together give the character his own face back.</p>
     */
    @Redirect(method = "openDialog", require = 0,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnoppes/npcs/controllers/data/Dialog;id:I"))
    private static int teras$sendTheWholeDialogue(Dialog read, Player reader,
                                                  EntityNPCInterface npc, Dialog stored) {
        if (read == stored && reader instanceof ServerPlayer player
                && !DungeonNpcs.dialogTokens(player).isEmpty()) {
            return -1;
        }
        return read.id;
    }

    /**
     * Keeps whatever the operator ticked in the editor, instead of the {@code true} the packet
     * branch writes over it.
     *
     * <p>That {@code hideNPC = true} is not an authoring decision, it is damage control: the branch
     * exists for dialogues with no NPC behind them, so the client draws a stand-in and CustomNPCs
     * hides it rather than show the wrong face. Our dialogues <i>do</i> have an NPC — the redirect
     * above is the only reason they came down this path — so the flag is left as authored and
     * {@code CnpcDialogPortraitMixin} makes the stand-in the real character.</p>
     */
    @Redirect(method = "openDialog", require = 0,
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                    target = "Lnoppes/npcs/controllers/data/Dialog;hideNPC:Z"))
    private static void teras$keepTheAuthoredPortrait(Dialog rendered, boolean hidden,
                                                      Player reader, EntityNPCInterface npc,
                                                      Dialog stored) {
        if (reader instanceof ServerPlayer player && !DungeonNpcs.dialogTokens(player).isEmpty()) {
            return;
        }
        rendered.hideNPC = hidden;
    }

    @WrapOperation(method = "openDialog", require = 0,
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/data/Dialog;"
                    + "copy(Lnet/minecraft/world/entity/player/Player;)"
                    + "Lnoppes/npcs/controllers/data/Dialog;"))
    private static Dialog teras$quoteThisFloorsPrices(Dialog stored, Player reader,
                                                      Operation<Dialog> copy) {
        Dialog rendered = copy.call(stored, reader);
        if (reader instanceof ServerPlayer player) {
            Map<String, String> tokens = DungeonNpcs.dialogTokens(player);
            if (!tokens.isEmpty()) {
                CnpcBridge.fillDialog(rendered, tokens);
            }
        }
        return rendered;
    }
}
