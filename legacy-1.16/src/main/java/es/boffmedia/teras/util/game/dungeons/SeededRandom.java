package es.boffmedia.teras.util.game.dungeons;

import java.util.Random;

public class SeededRandom {
    private Random random;

    public SeededRandom(String seed) {
        this.random = new Random(hashCode(seed));
    }

    private int hashCode(String str) {
        int hash = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            hash = ((hash << 5) - hash) + c;
            hash = hash & hash; // Convert to 32-bit integer
        }
        return hash;
    }

    public double random() {
        return random.nextDouble();
    }

    public int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public boolean randomChance(double p) {
        return random.nextDouble() < p;
    }
}

