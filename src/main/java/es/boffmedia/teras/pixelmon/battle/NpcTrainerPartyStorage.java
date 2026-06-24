package es.boffmedia.teras.pixelmon.battle;

import com.google.common.collect.Lists;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.api.storage.TrainerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.ClientSetPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.SetTempMode;
import com.pixelmonmod.pixelmon.entities.npcs.NPCTrainer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NpcTrainerPartyStorage extends TrainerPartyStorage {
    public static final int MAX_PARTY = 12;
    protected Pokemon[] party = new Pokemon[MAX_PARTY];



    public NpcTrainerPartyStorage(UUID uuid) {
        super(uuid);
    }

    public NpcTrainerPartyStorage(NPCTrainer trainer) {
        super(trainer);
    }



    public void copyToTemporaryMode(Color color) {
        Pokemon[] clonedParty = new Pokemon[MAX_PARTY];

        for(int i = 0; i < this.party.length; ++i) {
            Pokemon pokemon = this.party[i];
            if (pokemon != null) {
                clonedParty[i] = PokemonFactory.copy(pokemon);
            }
        }

        this.setInTemporaryMode(true, color, clonedParty);
    }

    public void enterTemporaryMode() {
        this.enterTemporaryMode(Color.RED);
    }

    public void enterTemporaryMode(Color color) {
        this.setInTemporaryMode(true, color);
    }

    @OnlyIn(Dist.CLIENT)
    public void setInTemporaryModeClient(boolean tempMode, Color color) {
        if (tempMode) {
            if (this.tempParty == null) {
                this.tempParty = new Pokemon[MAX_PARTY];
            }

            this.tempPartyColor = color;
        } else {
            this.tempParty = null;
            this.tempPartyColor = null;
        }

    }

    public void setInTemporaryMode(boolean tempMode, Color color, Pokemon... pokemons) {
        Iterator var4 = this.getPlayersToUpdate().iterator();

        while(var4.hasNext()) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity)var4.next();
            NetworkHelper.sendPacket(new SetTempMode(tempMode, color), serverPlayerEntity);
        }

        this.tempPartyColor = color;
        int i;
        if (tempMode) {
            this.tempParty = new Pokemon[MAX_PARTY];
            if (pokemons != null) {
                for(i = 0; i < MAX_PARTY; ++i) {
                    if (pokemons.length > i) {
                        this.tempParty[i] = pokemons[i];
                    }

                    Iterator var8 = this.getPlayersToUpdate().iterator();

                    while(var8.hasNext()) {
                        ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity)var8.next();
                        NetworkHelper.sendPacket(new ClientSetPacket(this, new StoragePosition(-1, i), this.tempParty[i], new EnumUpdateType[0]), serverPlayerEntity);
                    }
                }
            }
        } else {
            this.tempParty = null;
            this.setTempPartyColor((Color)null);

            for(i = 0; i < MAX_PARTY; ++i) {
                this.notifyListeners(new StoragePosition(-1, i), this.get(i), new EnumUpdateType[0]);
            }
        }

    }

    public void setTempPartyColor(Color tempPartyColor) {
        this.tempPartyColor = tempPartyColor;
        Iterator var2 = this.getPlayersToUpdate().iterator();

        while(var2.hasNext()) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity)var2.next();
            NetworkHelper.sendPacket(new SetTempMode(this.inTemporaryMode(), tempPartyColor), serverPlayerEntity);
        }

    }

    public Color getTempPartyColor() {
        if (this.tempPartyColor == null) {
            this.setTempPartyColor(Color.CYAN);
        }

        return this.tempPartyColor;
    }

    public Pokemon[] getAll() {
        return this.inTemporaryMode() ? (Pokemon[]) Arrays.copyOf(this.tempParty, MAX_PARTY) : (Pokemon[])Arrays.copyOf(this.party, MAX_PARTY);
    }

    public Pokemon[] getOriginalParty() {
        return (Pokemon[])Arrays.copyOf(this.party, MAX_PARTY);
    }

    public CompoundNBT writeToNBT(CompoundNBT nbt) {
        int i;
        for(i = 0; i < MAX_PARTY; ++i) {
            if (this.party[i] != null) {
                nbt.put("party" + i, this.party[i].writeToNBT(new CompoundNBT()));
            }
        }

        nbt.putBoolean("TempPartyEnabled", this.inTemporaryMode());
        if (this.inTemporaryMode()) {
            for(i = 0; i < this.tempParty.length; ++i) {
                if (this.tempParty[i] != null) {
                    nbt.put("TempParty" + i, this.tempParty[i].writeToNBT(new CompoundNBT()));
                }
            }

            nbt.putInt("TempPartyColor", this.getTempPartyColor().getRGB());
        }

        return nbt;
    }

    public PartyStorage readFromNBT(CompoundNBT nbt) {
        int i;
        for(i = 0; i < MAX_PARTY; ++i) {
            if (nbt.contains("party" + i)) {
                this.party[i] = PokemonFactory.create(nbt.getCompound("party" + i));
                this.party[i].setStorage(this, new StoragePosition(-1, i));
            } else {
                this.party[i] = null;
            }
        }

        if (nbt.getBoolean("TempPartyEnabled")) {
            this.tempParty = new Pokemon[MAX_PARTY];

            for(i = 0; i < MAX_PARTY; ++i) {
                if (nbt.contains("TempParty" + i)) {
                    this.tempParty[i] = PokemonFactory.create(nbt.getCompound("TempParty" + i));
                    this.tempParty[i].setStorage(this, new StoragePosition(-1, i));
                } else {
                    this.tempParty[i] = null;
                }
            }

            this.tempPartyColor = new Color(nbt.getInt("TempPartyColor"));
        }

        return this;
    }

    @Nullable
    public StoragePosition getFirstEmptyPosition() {
        for(int i = 0; i < MAX_PARTY; ++i) {
            if (this.inTemporaryMode() && this.tempParty[i] == null || !this.inTemporaryMode() && this.party[i] == null) {
                return new StoragePosition(-1, i);
            }
        }

        return null;
    }

    public void setOriginal(int slot, Pokemon pokemon) {
        this.setOriginal(new StoragePosition(-1, slot), pokemon);
    }

    public void setOriginal(StoragePosition position, Pokemon pokemon) {
        this.party[position.order] = pokemon;
        this.setNeedsSaving();
        if (pokemon != null) {
            pokemon.setStorage(this, position);
        }

        this.notifyListeners(position, pokemon, new EnumUpdateType[0]);
    }

    public void set(StoragePosition position, Pokemon pokemon) {
        if (this.inTemporaryMode()) {
            this.tempParty[position.order] = pokemon;
            this.setNeedsSaving();
            if (pokemon != null) {
                pokemon.setStorage(this, position);
            }

            this.notifyListeners(position, pokemon, new EnumUpdateType[0]);
        } else {
            this.party[position.order] = pokemon;
            this.setNeedsSaving();
            if (pokemon != null) {
                pokemon.setStorage(this, position);
            }

            this.notifyListeners(position, pokemon, new EnumUpdateType[0]);
        }
    }

    public void set(int slot, Pokemon pokemon) {
        this.set(new StoragePosition(-1, slot), pokemon);
    }

    @Nullable
    public Pokemon get(StoragePosition position) {
        if (position.order >= 0 && position.order <= this.party.length) {
            return this.inTemporaryMode() ? this.tempParty[position.order] : this.party[position.order];
        } else {
            return null;
        }
    }

    @Nullable
    public Pokemon get(int slot) {
        return this.get(this.cachePosition.set(-1, slot));
    }

    public Pokemon get(UUID pokemonUUID) {
        return this.get(this.getSlot(pokemonUUID));
    }

    public int getSlot(Pokemon pokemon) {
        return this.getSlot(pokemon.getUUID());
    }

    public int getSlot(UUID pokemonUUID) {
        for(int i = 0; i < MAX_PARTY; ++i) {
            if (this.inTemporaryMode() && this.tempParty[i] != null && Objects.equals(this.tempParty[i].getUUID(), pokemonUUID)) {
                return i;
            }

            if (this.party[i] != null && Objects.equals(this.party[i].getUUID(), pokemonUUID)) {
                return i;
            }
        }

        return -1;
    }

    public java.util.List<Pokemon> getTeam() {
        List<Pokemon> team = Lists.newArrayListWithCapacity(MAX_PARTY);
        Pokemon[] var2 = this.inTemporaryMode() ? this.tempParty : this.party;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Pokemon pokemon = var2[var4];
            if (pokemon != null && !pokemon.isEgg()) {
                team.add(pokemon);
            }
        }

        return team;
    }

    public void heal() {
        Iterator var1 = this.getTeam().iterator();

        while(var1.hasNext()) {
            Pokemon pokemon = (Pokemon)var1.next();
            pokemon.heal();
        }

    }

    public void swap(StoragePosition position1, StoragePosition position2) {
        Pokemon temp;
        if (this.inTemporaryMode()) {
            temp = this.tempParty[position1.order];
            this.tempParty[position1.order] = this.tempParty[position2.order];
            this.tempParty[position2.order] = temp;
            this.setNeedsSaving();
            if (this.tempParty[position1.order] != null) {
                this.tempParty[position1.order].setStorage(this, position1);
            }

            if (this.tempParty[position2.order] != null) {
                this.tempParty[position2.order].setStorage(this, position2);
            }

            this.notifyListeners(position1, this.tempParty[position1.order], new EnumUpdateType[0]);
            this.notifyListeners(position2, this.tempParty[position2.order], new EnumUpdateType[0]);
        } else {
            temp = this.party[position1.order];
            this.party[position1.order] = this.party[position2.order];
            this.party[position2.order] = temp;
            this.setNeedsSaving();
            if (this.party[position1.order] != null) {
                this.party[position1.order].setStorage(this, position1);
            }

            if (this.party[position2.order] != null) {
                this.party[position2.order].setStorage(this, position2);
            }

            this.notifyListeners(position1, this.party[position1.order], new EnumUpdateType[0]);
            this.notifyListeners(position2, this.party[position2.order], new EnumUpdateType[0]);
        }
    }

    public void swap(int slot1, int slot2) {
        this.swap(new StoragePosition(-1, slot1), new StoragePosition(-1, slot2));
    }

    public StoragePosition getPosition(Pokemon pokemon) {
        int i;
        if (this.inTemporaryMode()) {
            for(i = 0; i < MAX_PARTY; ++i) {
                if (this.tempParty[i] == pokemon || this.tempParty[i] != null && this.tempParty[i].getUUID().equals(pokemon.getUUID())) {
                    return new StoragePosition(-1, i);
                }
            }

            return null;
        } else {
            for(i = 0; i < MAX_PARTY; ++i) {
                if (this.party[i] == pokemon || this.party[i] != null && this.party[i].getUUID().equals(pokemon.getUUID())) {
                    return new StoragePosition(-1, i);
                }
            }

            return null;
        }
    }

}
