package es.boffmedia.teras.net.client;


import es.boffmedia.teras.Teras;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class CMessageRunJS implements Runnable{
    /** Hard cap on the inbound payload to avoid memory-amplification DoS. */
    private static final int MAX_LEN = 2048;

    /**
     * Whitelist of JavaScript functions the server is allowed to invoke in the embedded browser.
     * The browser context is treated as untrusted: only these named calls are permitted, so a
     * malicious/compromised server (or MITM) cannot run arbitrary JS on the client.
     */
    private static final Set<String> ALLOWED_FUNCTIONS = new HashSet<>(Arrays.asList(
            "frenteBatalla",
            "openDex",
            "takeScreenshot"
    ));

    /** Matches a single call to a bare function identifier, e.g. {@code frenteBatalla('a', 'b')}. */
    private static final Pattern CALL_PATTERN =
            Pattern.compile("^\\s*([A-Za-z_$][\\w$]*)\\s*\\(.*\\)\\s*;?\\s*$", Pattern.DOTALL);

    private String str;
    private PlayerEntity player;

    public CMessageRunJS(String str){
        this.str = str;
    }

    @Override
    public void run() {
        if (str == null || !isWhitelisted(str)) {
            Teras.getLogger().warn("Rejected non-whitelisted runJS payload from server");
            return;
        }
        Teras.PROXY.runJS(str);
    }

    private static boolean isWhitelisted(String js) {
        java.util.regex.Matcher matcher = CALL_PATTERN.matcher(js);
        if (!matcher.matches()) {
            return false;
        }
        return ALLOWED_FUNCTIONS.contains(matcher.group(1));
    }

    public static CMessageRunJS decode(PacketBuffer buf) {
        return new CMessageRunJS(buf.readUtf(MAX_LEN));
    }

    public void encode(PacketBuffer buf) {
        buf.writeUtf(str, MAX_LEN);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        player = contextSupplier.get().getSender();
        contextSupplier.get().enqueueWork((Runnable) this);
        contextSupplier.get().setPacketHandled(true);
    }

}
