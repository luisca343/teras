package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.RandomHelper;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.ParticipantSelection;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.TeamSelection;
import com.pixelmonmod.pixelmon.battles.controller.participants.EntityParticipant;
import es.boffmedia.teras.battle.pixelmon.TerasTeamPreview;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Re-applies a Teras battle's type, clauses, end-handler and gimmick flags to the battle started from
 * the native team-preview screen, keyed by the player via {@link TerasTeamPreview}. Needed because
 * {@code TeamSelection.startBattle} rebuilds via {@code BattleBuilder…rules(this.rules).start()}
 * without {@code setBattleType}, and battle type is a {@code StoredContext} defaulting to SINGLE that
 * no {@code BattleRuleSet} carries. Selections with no stash entry are left as vanilla.
 */
@Mixin(TeamSelection.class)
public abstract class TeamSelectionMixin {

    @Shadow
    protected ParticipantSelection[] participants;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "startBattle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/pixelmonmod/pixelmon/battles/api/BattleBuilder;rules(Lnet/minecraft/core/Holder;)Lcom/pixelmonmod/pixelmon/battles/api/BattleBuilder;"))
    private BattleBuilder teras$applyTerasBattle(BattleBuilder builder, Holder rules) {
        BattleBuilder configured = builder.rules(rules);
        ServerPlayer player = teras$findPlayer();
        if (player == null) {
            return configured;
        }
        TerasTeamPreview.PendingBattle pending = TerasTeamPreview.consume(player.getUUID());
        if (pending != null && pending.builderCustomizer() != null) {
            pending.builderCustomizer().accept(configured);
        }
        return configured;
    }

    /** Sets the trainer AI and gimmick flags on the rebuilt NPC (its builder defaults canMega/canDynamax
     *  to false and the AI to {@code RANDOM}). Peeks the stash; the later {@code rules()} redirect
     *  consumes it. */
    @Redirect(
            method = "startBattle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/pixelmonmod/pixelmon/battles/controller/participants/EntityParticipant$Builder;build()Lcom/pixelmonmod/pixelmon/battles/controller/participants/EntityParticipant;"))
    private EntityParticipant teras$applyGimmicks(EntityParticipant.Builder builder) {
        ServerPlayer player = teras$findPlayer();
        if (player != null) {
            TerasTeamPreview.PendingBattle pending = TerasTeamPreview.peek(player.getUUID());
            if (pending != null) {
                builder = builder.aiMode(pending.aiMode())
                        .canMega(pending.canMega()).canDynamax(pending.canDynamax());
            }
        }
        return builder.build();
    }

    /** Fields the NPC's full (pre-trimmed) team rather than the screen's random pick, which is sized by
     *  the player's cap and would shrink an asymmetric rival. addTeamMember skips empty slots. */
    @Redirect(
            method = "startBattle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/pixelmonmod/pixelmon/api/util/helpers/RandomHelper;getRandomDistinctNumbersBetween(III)[I"))
    private int[] teras$fullNpcTeam(int start, int end, int count) {
        ServerPlayer player = teras$findPlayer();
        if (player != null && TerasTeamPreview.peek(player.getUUID()) != null) {
            return new int[]{0, 1, 2, 3, 4, 5};
        }
        return RandomHelper.getRandomDistinctNumbersBetween(start, end, count);
    }

    private ServerPlayer teras$findPlayer() {
        if (participants == null) {
            return null;
        }
        for (ParticipantSelection selection : participants) {
            ParticipantSelectionAccessor accessor = (ParticipantSelectionAccessor) selection;
            if (accessor.teras$isNPC()) {
                continue;
            }
            PartyStorage storage = accessor.teras$getStorage();
            if (storage instanceof PlayerPartyStorage playerStorage) {
                return playerStorage.getPlayer();
            }
        }
        return null;
    }
}
