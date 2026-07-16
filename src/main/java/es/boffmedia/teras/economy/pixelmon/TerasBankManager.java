package es.boffmedia.teras.economy.pixelmon;

import com.pixelmonmod.pixelmon.api.economy.BankAccount;
import com.pixelmonmod.pixelmon.api.economy.BankAccountManager;
import es.boffmedia.teras.economy.EconomyStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Installs Teras as Pixelmon's bank, replacing its built-in storage. Port of the 1.16.5 Wungill
 * {@code PixelBankManager}.
 *
 * <p>The account is a stateless view over {@link EconomyStore}, so it is created on demand rather than
 * pooled, and the future is always already complete: the underlying read is a cache hit or a miss, and
 * a miss is answered as "unknown" rather than waited on (see {@link EconomyStore}).</p>
 *
 * <p>The 9.0.6 → 9.3.16 signature change is here: {@code getBankAccount} returned {@code Optional} on
 * 1.16.5 and returns {@link CompletableFuture} now.</p>
 *
 * <p><b>Pixelmon-coupled.</b> Only ever named from {@link PixelmonEconomyHook}, which the JVM does not
 * load until Pixelmon has been confirmed present.</p>
 */
public class TerasBankManager implements BankAccountManager {

    @Override
    public CompletableFuture<? extends BankAccount> getBankAccount(UUID uuid) {
        return CompletableFuture.completedFuture(new TerasBankAccount(uuid));
    }

    @Override
    public BankAccount getBankAccountNow(UUID uuid) {
        return new TerasBankAccount(uuid);
    }
}
