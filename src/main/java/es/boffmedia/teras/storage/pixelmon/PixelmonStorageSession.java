package es.boffmedia.teras.storage.pixelmon;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.battle.tower.BattleTower;
import es.boffmedia.teras.storage.api.StorageSession;
import es.boffmedia.teras.storage.model.PcEntry;
import es.boffmedia.teras.storage.model.StoredMon;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Pixelmon party + PC, already loaded. Server thread only — see {@link StorageSession}. */
final class PixelmonStorageSession implements StorageSession {

    private final UUID player;
    private final PlayerPartyStorage party;
    private final PCStorage pc;

    PixelmonStorageSession(UUID player, PlayerPartyStorage party, PCStorage pc) {
        this.player = player;
        this.party = party;
        this.pc = pc;
    }

    @Override
    public List<PcEntry> readPc() {
        if (pc == null) {
            return List.of();
        }
        List<PcEntry> entries = new ArrayList<>();
        for (int box = 0; box < pc.getBoxCount(); box++) {
            PCBox pcBox = pc.getBox(box);
            if (pcBox == null) {
                continue;
            }
            for (int slot = 0; slot < PCBox.POKEMON_PER_BOX; slot++) {
                Pokemon pokemon = pcBox.get(slot);
                if (pokemon == null || pokemon.getSpecies() == null) {
                    continue;
                }
                entries.add(new PcEntry(box, slot, PixelmonMons.read(pokemon)));
            }
        }
        return entries;
    }

    @Override
    public List<StoredMon> readParty() {
        // getOriginalParty(), not get(slot): the latter redirects to a throwaway `tempParty` during
        // team selection and battle-tower runs, reporting a rental team as the trainer's own.
        Pokemon[] slots = party == null ? new Pokemon[0] : party.getOriginalParty();
        List<StoredMon> team = new ArrayList<>(PartyStorage.MAX_PARTY);
        for (int slot = 0; slot < PartyStorage.MAX_PARTY; slot++) {
            Pokemon pokemon = slot < slots.length ? slots[slot] : null;
            team.add(pokemon == null || pokemon.getSpecies() == null
                    ? null : PixelmonMons.read(pokemon));
        }
        return team;
    }

    @Override
    public boolean swap(int sourceBox, int sourceIndex, int destBox, int destIndex) {
        boolean sourceInParty = sourceBox == PARTY_BOX;
        boolean destInParty = destBox == PARTY_BOX;
        boolean touchesParty = sourceInParty || destInParty;
        boolean touchesPc = !sourceInParty || !destInParty;

        if (touchesParty && party == null) {
            Teras.LOGGER.warn("pc/move: no party for {}", player);
            return false;
        }
        if (touchesPc && pc == null) {
            Teras.LOGGER.warn("pc/move: no PC for {}", player);
            return false;
        }
        int boxCount = pc == null ? 0 : pc.getBoxCount();
        if (!inRange(boxCount, sourceBox, sourceIndex) || !inRange(boxCount, destBox, destIndex)) {
            return false;
        }
        // PC-to-PC stays allowed: it is invisible to whatever the party is doing.
        if (touchesParty && partyIsBusy()) {
            Teras.LOGGER.warn("pc/move: refusing a party swap for {} — the party is in use", player);
            return false;
        }

        if (sourceInParty && destInParty) {
            party.swap(sourceIndex, destIndex);
        } else if (!touchesParty) {
            pc.swap(sourceBox, sourceIndex, destBox, destIndex);
        } else {
            crossSwap(sourceInParty ? sourceIndex : destIndex,
                    sourceInParty ? destBox : sourceBox,
                    sourceInParty ? destIndex : sourceIndex);
        }
        return true;
    }

    /**
     * Pixelmon's {@code swap} spans one storage and {@code transfer} needs an empty destination, so
     * a party↔PC exchange is four writes. Both slots are cleared before either is filled: a Pokémon
     * momentarily present twice is one Pixelmon's duplicate handling drops.
     */
    private void crossSwap(int partySlot, int box, int boxSlot) {
        Pokemon fromParty = party.get(partySlot);
        Pokemon fromPc = pc.get(box, boxSlot);
        party.set(partySlot, null);
        pc.set(box, boxSlot, null);
        party.set(partySlot, fromPc);
        pc.set(box, boxSlot, fromParty);
    }

    /**
     * Whether the party is currently something other than the trainer's own six.
     *
     * <p>{@code inTemporaryMode} is load-bearing, not just a stricter battle test: in that mode
     * {@code set} writes into a {@code tempParty} array that is discarded afterwards, so a cross
     * swap would <b>destroy</b> the Pokémon taken out of the PC. Team selection enters it before a
     * battle exists in {@code BattleRegistry}.</p>
     */
    private boolean partyIsBusy() {
        if (party.inTemporaryMode()) {
            return true;
        }
        ServerPlayer owner = party.getOwner();
        // A null owner is offline, and so in no battle — the normal case for a web PC.
        return owner != null && (BattleTower.isActive(owner) || BattleRegistry.getBattle(owner) != null);
    }

    /** Out of range is a hard no, never a clamp: landing a Pokémon elsewhere is how one goes missing. */
    private static boolean inRange(int boxCount, int box, int index) {
        if (box == PARTY_BOX) {
            return index >= 0 && index < PartyStorage.MAX_PARTY;
        }
        return box >= 0 && box < boxCount && index >= 0 && index < PCBox.POKEMON_PER_BOX;
    }

}
