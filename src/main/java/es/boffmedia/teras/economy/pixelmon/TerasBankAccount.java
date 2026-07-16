package es.boffmedia.teras.economy.pixelmon;

import com.pixelmonmod.pixelmon.api.economy.BankAccount;
import es.boffmedia.teras.economy.EconomyStore;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapts one player's {@link EconomyStore} balance to Pixelmon's {@link BankAccount}, so in-game
 * PokéDollars <i>are</i> the starbank balance. Port of the 1.16.5 Wungill {@code PixelBank}.
 *
 * <p>Pixelmon calls this on the server thread and expects it not to block, so every method is a cache
 * read or a cache write plus a fire-and-forget POST.</p>
 *
 * <p><b>Pixelmon-coupled.</b> Only ever named from {@link PixelmonEconomyHook}, which the JVM does not
 * load until Pixelmon has been confirmed present.</p>
 */
public class TerasBankAccount implements BankAccount {
    private final UUID playerId;

    public TerasBankAccount(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public UUID getIdentifier() {
        return playerId;
    }

    @Override
    public BigDecimal getBalance() {
        return EconomyStore.get(playerId);
    }

    @Override
    public void setBalance(BigDecimal amount) {
        EconomyStore.set(playerId, amount);
    }

    @Override
    public boolean hasBalance(BigDecimal amount) {
        return EconomyStore.has(playerId, amount);
    }

    /**
     * {@code false} when the balance is unknown or short — Pixelmon reads this to decide whether a
     * purchase went through, so reporting success without debiting would hand out free goods (which is
     * what 1.16.5 did; see {@link EconomyStore#withdraw}).
     */
    @Override
    public boolean take(BigDecimal amount) {
        return EconomyStore.withdraw(playerId, amount);
    }

    @Override
    public boolean add(BigDecimal amount) {
        return EconomyStore.deposit(playerId, amount);
    }
}
