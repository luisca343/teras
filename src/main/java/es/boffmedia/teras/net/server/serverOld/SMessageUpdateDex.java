package es.boffmedia.teras.net.server.serverOld;

import com.google.common.base.Charsets;
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
        SMessageUpdateDex message = new SMessageUpdateDex(buf.readInt(), buf.toString(Charsets.UTF_8), buf.toString(Charsets.UTF_8));
        return message;
    }

    public void encode(PacketBuffer buf) {
        buf.writeInt(dex);
        buf.writeCharSequence(form, Charsets.UTF_8);
        buf.writeCharSequence(palette, Charsets.UTF_8);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }
}
