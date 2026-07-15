package es.boffmedia.teras.util.data;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;

public class PixelmonStorageHelper {
    public static PlayerPartyStorage getParty(UUID uuid) {
        return StorageProxy.getParty(uuid);
    }

    public static PCStorage getPC(UUID uuid) {
        return StorageProxy.getPCForPlayer(uuid);
    }

    public static boolean movePcToParty(UUID uuid, int box, int slotInBox) {
        PlayerPartyStorage party = getParty(uuid);
        PCStorage pc = getPC(uuid);

        StoragePosition fromPc = pc.getPosition(pc.get(box, slotInBox));
        if (fromPc == null) return false;

        StoragePosition toParty = party.getFirstEmptyPosition();
        if (toParty == null) return false; // party full

        return party.transfer(pc, fromPc, toParty);
    }

    public static boolean movePartyToPc(UUID uuid, int partySlot) {
        PlayerPartyStorage party = getParty(uuid);
        PCStorage pc = getPC(uuid);

        StoragePosition fromParty = party.getPosition(party.get(partySlot));
        if (fromParty == null) return false;

        StoragePosition toPc = pc.getFirstEmptyPosition();
        if (toPc == null) return false; // pc full

        return pc.transfer(party, fromParty, toPc);
    }

    public static boolean swapPC(UUID uuid, int box1, int slotInBox1, int box2, int slotInBox2) {
        PCStorage pc = getPC(uuid);

        pc.swap(box1, slotInBox1, box2, slotInBox2);

        return true;
    }

    public static boolean swapParty(UUID uuid, int slot1, int slot2) {
        PlayerPartyStorage party = getParty(uuid);

        party.swap(slot1, slot2);

        return true;
    }

    public static boolean swapPcWithParty(UUID uuid, int box, int slotInBox, int partySlot) {
        PlayerPartyStorage party = getParty(uuid);
        PCStorage pc = getPC(uuid);

        // Get the actual Pokemon objects first
        Pokemon pcPokemon = pc.get(box, slotInBox);
        Pokemon partyPokemon = party.get(partySlot);

        // If either slot is empty, we can't swap
        //if (pcPokemon == null || partyPokemon == null) return false;

        party.doWithoutSendingUpdates(() -> {
            pc.doWithoutSendingUpdates(() -> {
                party.set(partySlot, null);
                pc.set(box, slotInBox, null);

                // Then set them in their new positions
                party.set(partySlot, pcPokemon);
                pc.set(box, slotInBox, partyPokemon);
            });
        });

        try {
            Pokemon verifyParty = party.get(partySlot);
            if (verifyParty != null && verifyParty.equals(pcPokemon)) {
                party.set(partySlot, pcPokemon);
            } else {
                party.set(partySlot, null);
            }

            Pokemon verifyPC = pc.get(box, slotInBox);
            if (verifyPC != null && verifyPC.equals(partyPokemon)) {
                pc.set(box, slotInBox, partyPokemon);
            } else {
                pc.set(box, slotInBox, null);
            }

        } catch (Exception e) {
            return false;
        }

        return true;
    }


}