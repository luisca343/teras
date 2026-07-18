package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.TrackPoint;

import java.util.UUID;

/**
 * Everything a race needs to do to the world. {@link RaceCore} decides <i>when</i>; an implementation
 * decides <i>how</i> — the live one drives Immersive Vehicles and sends packets, the test one records
 * calls, which is what lets the whole race state machine be unit-tested without a game.
 *
 * <p>All methods are called on the server thread, in tick order.</p>
 */
public interface RaceCallbacks {

    /** Puts a kart on the grid with this racer in it, held by the parking brake until the start. */
    void placeOnGrid(UUID player, TrackPoint slot);

    /** Drops the grid hold on every kart. The "GO" of the countdown. */
    void releaseAll();

    /** Removes this racer's kart — on finishing, retiring, or being eliminated. */
    void removeKart(UUID player);

    /**
     * Tries to put a racer back in their kart after they came out of it. Returning false means it
     * could not be done, and the racer is heading for a DNF when the grace period runs out.
     */
    boolean reseat(UUID player);

    void title(UUID player, String title, String subtitle);

    void message(UUID player, String message);

    void broadcast(String message);

    void sound(UUID player, RaceSound sound);

    /** Pushes the racer's HUD state; a no-op until the client HUD exists. */
    void hud(UUID player, RaceHudState state);

    /** The race is over. Rewards, leaderboards and reporting hang off this. */
    void finished(RaceResult result);
}
