package es.boffmedia.teras.client.gui;

import es.boffmedia.teras.Teras;
import journeymap.client.ui.fullscreen.Fullscreen;

public class FullscreenMap extends Fullscreen {
    public FullscreenMap() {
        super();
    }

    @Override
    public boolean keyPressed(int key, int value, int modifier) {
        Teras.getLogger().info("Key pressed123: " + key + " " + value + " " + modifier);
        return super.keyPressed(key, value, modifier);
    }
}
