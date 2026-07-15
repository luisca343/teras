package es.boffmedia.teras.mixin;

import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.battles.api.rules.teamselection.ParticipantSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to {@link ParticipantSelection}'s {@code protected final} fields, so
 * {@link TeamSelectionMixin} can locate the human player within a team-selection session.
 */
@Mixin(ParticipantSelection.class)
public interface ParticipantSelectionAccessor {

    @Accessor("storage")
    PartyStorage teras$getStorage();

    @Accessor("isNPC")
    boolean teras$isNPC();
}
