package es.boffmedia.teras.net.server;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.PokedexEvent;
import com.pixelmonmod.pixelmon.api.pokedex.PlayerPokedex;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexRegistrationStatus;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.montoyo.mcef.api.IJSQueryCallback;

import java.util.function.Supplier;

public class SMessageUpdateDex implements Runnable{
    /** Hard cap on inbound string fields to avoid memory-amplification DoS. */
    private static final int MAX_LEN = 64;

    private int dex;
    private String form;
    private String palette;
    private ServerPlayerEntity player;
    private IJSQueryCallback callback;

    public SMessageUpdateDex(int dex, String form, String palette){
        this.dex = dex;
        this.form = form;
        this.palette = palette;
    }

    @Override
    public void run() {
        // AUTHORITY: this only ever registers a SEEN entry in the SENDER's own Pokédex, so the
        // sender is inherently the authority. Validate the dex id so a malformed/forged packet
        // can't throw on the empty Optional from PixelmonSpecies.fromDex.
        if (player == null) {
            return;
        }
        if (!PixelmonSpecies.fromDex(dex).isPresent()) {
            return;
        }
        Pokemon pokemon = PokemonFactory.create(PixelmonSpecies.fromDex(dex).get());
        pokemon.setForm(form);
        pokemon.setForm(palette);

        PokedexEvent.Post event = new PokedexEvent.Post(player.getUUID(), PokedexRegistrationStatus.UNKNOWN, pokemon, PokedexRegistrationStatus.SEEN, "SmartRotom");
        Pixelmon.EVENT_BUS.post(event);

        PlayerPokedex pokedex = new PlayerPokedex(player.getUUID());

        pokedex.set(dex, PokedexRegistrationStatus.SEEN);
        pokedex.update();
    }

    public static SMessageUpdateDex decode(PacketBuffer buf) {
        // readUtf advances the reader index (the old buf.toString did not, so form and palette
        // both received the entire remaining buffer) and length-caps each field.
        return new SMessageUpdateDex(buf.readInt(), buf.readUtf(MAX_LEN), buf.readUtf(MAX_LEN));
    }

    public void encode(PacketBuffer buf) {
        buf.writeInt(dex);
        buf.writeUtf(form == null ? "" : form, MAX_LEN);
        buf.writeUtf(palette == null ? "" : palette, MAX_LEN);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
