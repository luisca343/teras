package es.boffmedia.teras.plot.model;

import java.util.Set;
import java.util.UUID;

/**
 * Who holds a plot, as one immutable row of the in-memory snapshot. The durable copy lives in
 * SQLite ({@code plot} + {@code plot_member}); this is what the block-event hot path reads, since
 * that path must never touch the database.
 *
 * <p>A plot is "a region with an ownership row" rather than "a region with a flag set" — that is
 * what keeps the implicit deny-unless-owner rule from ever firing on a {@code pueblo_*} or
 * {@code carretera_*} region, which behave as WorldGuard's {@code passthrough}. Nothing can buy
 * them, so no row exists, so the rule cannot reach them.</p>
 *
 * <p>Pure Java — no Minecraft imports — so the resolver stays unit-testable.</p>
 */
public record PlotOwnership(String regionName, String dimension, UUID owner, long ownedSince,
                            Long expiresAt, Set<UUID> members) {

    public PlotOwnership {
        members = members == null ? Set.of() : Set.copyOf(members);
    }

    /** Unowned plots still have a row: an admin listed them for sale, nobody has bought yet. */
    public boolean isOwned() {
        return owner != null;
    }

    /**
     * Whether {@code player} may build here. Owner and members both pass; only the owner may
     * change the member list or resell, which is enforced at the command layer, not here.
     */
    public boolean allows(UUID player) {
        if (player == null) return false;
        return player.equals(owner) || members.contains(player);
    }

    /**
     * Whether the lease has run out as of {@code now}. Freehold plots ({@code expiresAt} null)
     * never expire; the field exists so rentals can land without a migration.
     */
    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
