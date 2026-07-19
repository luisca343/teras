package es.boffmedia.teras.region;

import es.boffmedia.teras.plot.model.PlotOwnership;
import es.boffmedia.teras.region.model.RegionFlag;
import es.boffmedia.teras.region.model.TerasRegion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The flag-resolution rules, split out of {@link RegionIndex} so they can be unit-tested without a
 * Minecraft runtime — {@link RegionStore} reaches for {@code FMLPaths} on load, which no test can
 * satisfy. Everything here takes a plain region list; {@link RegionIndex} supplies the snapshot.
 *
 * <p>Pure Java on purpose, same as {@link TerasRegion}.</p>
 */
public final class RegionResolver {
    private RegionResolver() {}

    /**
     * The order every resolution walk expects: highest priority first. Sorting once in the
     * snapshot is what lets the walks below stop early instead of collecting candidates into a
     * per-call list — explosions resolve once per affected block.
     */
    public static List<TerasRegion> ordered(Collection<TerasRegion> regions) {
        List<TerasRegion> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparingInt(TerasRegion::getPriority).reversed());
        return List.copyOf(sorted);
    }

    /**
     * Whether {@code flag} is denied at the position, given the region list in {@link #ordered}
     * order. Only the regions at the highest priority among those containing the point get a say
     * (WorldGuard shadowing); within that tier it is most-restrictive-wins, and a tier where
     * nobody has an opinion allows.
     *
     * <p>Shadowing is what lets a plot be <em>more</em> permissive than the town around it: at
     * equal priority the town's {@code deny} would always win, so the plot could never reopen a
     * flag its town closes.</p>
     */
    public static boolean denies(List<TerasRegion> ordered, double x, double y, double z,
                                 RegionFlag flag) {
        int tier = Integer.MIN_VALUE;
        for (TerasRegion region : ordered) {
            if (tier != Integer.MIN_VALUE && region.getPriority() < tier) break;
            if (!region.contains(x, y, z)) continue;
            tier = region.getPriority();
            if (region.deniesFlag(flag)) return true;
        }
        return false;
    }

    /** Why an action was refused, so the player can be told something more useful than "no". */
    public enum Outcome {
        ALLOW,
        /** A region in the winning tier has the flag set to {@code denegar}. */
        DENIED_BY_FLAG,
        /** The winning tier contains a plot and the player is neither its owner nor a member. */
        DENIED_BY_PLOT
    }

    /**
     * The verdict, naming the region responsible so the feedback can say which one and, for a
     * plot, who holds it.
     */
    public record Decision(Outcome outcome, String regionName, UUID owner) {
        public boolean denied() {
            return outcome != Outcome.ALLOW;
        }
    }

    private static final Decision ALLOWED = new Decision(Outcome.ALLOW, null, null);

    /** Ownership of a region by name, or {@code null} when that region is not a plot. */
    @FunctionalInterface
    public interface PlotLookup {
        PlotOwnership ownershipOf(String regionName);

        PlotLookup NONE = name -> null;
    }

    /**
     * Full resolution, including plots. Same shadowing as {@link #denies}, plus the rule that
     * inverts the default inside a plot:
     *
     * <ol>
     *   <li>Keep the regions containing the point at the highest priority.</li>
     *   <li>If any of them is a plot and {@code player} is not its owner or a member, deny —
     *       implicitly, with no flag set anywhere. Being inside a plot you do not hold <em>is</em>
     *       the denial.</li>
     *   <li>Otherwise fall back to the explicit-flag check.</li>
     * </ol>
     *
     * <p>Step 2 is why regions that are not plots need no {@code passthrough} opt-out: a plot is
     * "a region with an ownership row", so a region nobody can buy has no row and the rule cannot
     * reach it. Every {@code pueblo_*} and {@code carretera_*} that ships today keeps behaving
     * exactly as it does now.</p>
     *
     * <p>Holding one plot in the tier is enough — overlapping plots at equal priority do not
     * each have to admit you — but membership only bypasses the <em>implicit</em> denial; an
     * explicit {@code denegar} in the tier still applies to owners.</p>
     *
     * <p>{@code now} is passed rather than read so the rules stay pure; it only matters once
     * rentals set {@code expiresAt}, where a lapsed lease reverts the plot to unowned.</p>
     */
    public static Decision decide(List<TerasRegion> ordered, double x, double y, double z,
                                  RegionFlag flag, UUID player, PlotLookup plots, long now) {
        boolean gated = flag.isOwnershipGated();
        int tier = Integer.MIN_VALUE;
        String plotName = null;
        UUID plotOwner = null;
        boolean holdsAPlot = false;
        String deniedBy = null;

        for (TerasRegion region : ordered) {
            if (tier != Integer.MIN_VALUE && region.getPriority() < tier) break;
            if (!region.contains(x, y, z)) continue;
            tier = region.getPriority();

            if (gated) {
                PlotOwnership plot = plots.ownershipOf(region.getName());
                if (plot != null) {
                    if (plotName == null) {
                        plotName = region.getName();
                        plotOwner = plot.isExpired(now) ? null : plot.owner();
                    }
                    if (!plot.isExpired(now) && plot.allows(player)) holdsAPlot = true;
                }
            }
            if (deniedBy == null && region.deniesFlag(flag)) deniedBy = region.getName();
        }

        if (plotName != null && !holdsAPlot) {
            return new Decision(Outcome.DENIED_BY_PLOT, plotName, plotOwner);
        }
        if (deniedBy != null) return new Decision(Outcome.DENIED_BY_FLAG, deniedBy, null);
        return ALLOWED;
    }
}
