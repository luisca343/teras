package es.boffmedia.teras.karts.engine;

import es.boffmedia.teras.karts.model.TrackPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link RaceCallbacks} that records what it was asked to do instead of doing it, so a race can be
 * driven tick by tick in a test and its behaviour asserted on.
 *
 * <p>Public so the mode tests can drive a race too.</p>
 */
public final class RecordingCallbacks implements RaceCallbacks {

    public record TitleCall(UUID player, String title, String subtitle) {}

    public final Map<UUID, TrackPoint> gridPlacements = new LinkedHashMap<>();
    public final List<UUID> removedKarts = new ArrayList<>();
    public final List<UUID> reseatAttempts = new ArrayList<>();
    public final List<TitleCall> titles = new ArrayList<>();
    public final List<String> broadcasts = new ArrayList<>();
    public final Map<UUID, List<String>> messages = new LinkedHashMap<>();
    public final List<RaceSound> sounds = new ArrayList<>();
    public final Map<UUID, RaceHudState> lastHud = new LinkedHashMap<>();
    public RaceResult result;
    public int releaseCount;

    /** Whether {@link #reseat} succeeds; false simulates a kart that has gone for good. */
    public boolean reseatSucceeds = true;

    @Override
    public void placeOnGrid(UUID player, TrackPoint slot) {
        gridPlacements.put(player, slot);
    }

    @Override
    public void releaseAll() {
        releaseCount++;
    }

    @Override
    public void removeKart(UUID player) {
        removedKarts.add(player);
    }

    @Override
    public boolean reseat(UUID player) {
        reseatAttempts.add(player);
        return reseatSucceeds;
    }

    @Override
    public void title(UUID player, String title, String subtitle) {
        titles.add(new TitleCall(player, title, subtitle));
    }

    @Override
    public void message(UUID player, String message) {
        messages.computeIfAbsent(player, key -> new ArrayList<>()).add(message);
    }

    @Override
    public void broadcast(String message) {
        broadcasts.add(message);
    }

    @Override
    public void sound(UUID player, RaceSound sound) {
        sounds.add(sound);
    }

    @Override
    public void hud(UUID player, RaceHudState state) {
        lastHud.put(player, state);
    }

    @Override
    public void finished(RaceResult result) {
        this.result = result;
    }

    // --- helpers ---------------------------------------------------------------------------

    public List<String> titlesFor(UUID player) {
        return titles.stream().filter(call -> call.player().equals(player))
                .map(TitleCall::title).toList();
    }

    public boolean sawTitleContaining(UUID player, String fragment) {
        return titles.stream().anyMatch(call -> call.player().equals(player)
                && (call.title().contains(fragment) || call.subtitle().contains(fragment)));
    }

    public List<String> messagesFor(UUID player) {
        return messages.getOrDefault(player, List.of());
    }

    public boolean sawMessageContaining(UUID player, String fragment) {
        return messagesFor(player).stream().anyMatch(message -> message.contains(fragment));
    }
}
