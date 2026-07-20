package es.boffmedia.teras.dungeon.piso;

/**
 * One authored template a room key may resolve to. A key with several is how a piso varies its
 * rooms between runs.
 *
 * <p>Held as strings and degrees rather than {@code ResourceLocation} and {@code Rotation} so the
 * piso model stays free of Minecraft and can be validated in tests; the build layer resolves them.
 * </p>
 *
 * @param template structure id, e.g. {@code teras:dungeon/cuevas/normal_b}
 * @param weight   relative draw weight
 * @param rotation 0/90/180/270 — one authored template reused at several orientations
 */
public record RoomVariant(String template, int weight, int rotation) {

    public RoomVariant {
        weight = Math.max(1, weight);
        rotation = ((rotation % 360) + 360) % 360;
        rotation = switch (rotation) {
            case 90, 180, 270 -> rotation;
            default -> 0;
        };
    }

    /**
     * The template a room key resolves to when the piso declares no variants for it:
     * {@code teras:dungeon/<piso>/<key>}. A convention <i>within</i> the piso, not a fallback to
     * another one — a piso that has not authored the room still has nowhere to borrow it from.
     */
    public static RoomVariant conventional(String pisoId, String roomKey) {
        return new RoomVariant("teras:dungeon/" + pisoId + "/" + roomKey, 1, 0);
    }
}
