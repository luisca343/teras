package es.boffmedia.teras.dungeon.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a rig's boxes actually end up once GeckoLib has loaded it.
 *
 * <p>Every other check in this package reads the JSON as text: does the bone exist, is the clip
 * named, is the file in the jar. All of those passed while every spider in the dungeon stood with
 * its legs in the air, because the fault was not in what the file said — it was in what the numbers
 * meant after the loader had transformed them.</p>
 *
 * <h2>The transform</h2>
 *
 * <p>Taken from {@code BakedModelFactory} in geckolib-neoforge-1.21.1-4.9.2:</p>
 * <ul>
 *   <li>a model is <b>mirrored in X</b> on load — a cube's origin becomes
 *       {@code -(origin.x + size.x)} and every pivot's x is negated;</li>
 *   <li>a rotation's X and Y are negated and its Z is not;</li>
 *   <li>rotations compose Z, then Y, then X.</li>
 * </ul>
 *
 * <p>The consequence that cost two releases: reflecting a rotation through a plane reverses it, so
 * a <b>Z rotation runs backwards</b> relative to the coordinates the file is written in. A leg
 * authored to bend down bends up. {@code tools/preview_rig.py} draws the same transform to a PNG
 * for looking at; this is the half that fails a build.</p>
 */
class RigGeometryTest {

    /** A foot this far from the floor reads as hovering or as sunk into it. */
    private static final double GROUND_TOLERANCE = 1.5;

    /** Both flanks come out of the same arithmetic with one sign flipped, so any drift is a fault. */
    private static final double MIRROR_TOLERANCE = 1e-6;

    @Test
    void everyRigStandsOnItsFeet() {
        List<String> problems = new ArrayList<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            problems.addAll(check(variant.model()));
        }
        assertTrue(problems.isEmpty(), "rig geometry:\n" + String.join("\n", problems));
    }

    /** Bone to {min corner, max corner} once loaded, empty when the rig is not in the jar. */
    private static Map<String, double[][]> extents(String model) {
        Map<String, double[][]> extents = new HashMap<>();
        String json = read("assets/teras/" + model);
        if (json == null) {
            return extents;   // BestiaryAuditTest owns the missing-file case
        }
        JsonArray bones = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            if (bone.get("parent") == null) {
                walk(bones, bone.get("name").getAsString(), identity(), extents);
            }
        }
        return extents;
    }

    private static List<String> check(String model) {
        List<String> problems = new ArrayList<>();
        Map<String, double[][]> extents = extents(model);
        if (extents.isEmpty()) {
            return problems;
        }

        double torsoTop = -1e9;
        double bodyBottom = 1e9;
        for (Map.Entry<String, double[][]> e : extents.entrySet()) {
            if (!isLeg(e.getKey())) {
                torsoTop = Math.max(torsoTop, e.getValue()[1][1]);
            }
            if (e.getKey().equals("body") || e.getKey().equals("thorax")) {
                bodyBottom = Math.min(bodyBottom, e.getValue()[0][1]);
            }
        }

        // Per leg, not per segment: a coxa is meant to stay up beside the body, and only the last
        // segment of the chain reaches the floor.
        Map<String, double[]> legs = new HashMap<>();
        for (Map.Entry<String, double[][]> e : extents.entrySet()) {
            if (!isLeg(e.getKey())) {
                continue;
            }
            String chain = e.getKey().split("_femur")[0].split("_tibia")[0];
            double[] span = legs.computeIfAbsent(chain, k -> new double[]{1e9, -1e9});
            span[0] = Math.min(span[0], e.getValue()[0][1]);
            span[1] = Math.max(span[1], e.getValue()[1][1]);
        }

        for (String leg : new TreeSet<>(legs.keySet())) {
            double low = legs.get(leg)[0];
            double high = legs.get(leg)[1];
            if (Math.abs(low) > GROUND_TOLERANCE) {
                problems.add(String.format("%s %s: foot rests at y%.1f, not on the floor at y0 —"
                        + " the model %s the ground", model, leg, low,
                        low < 0 ? "sinks into" : "hovers over"));
            }
            if (bodyBottom < 1e8 && low >= bodyBottom) {
                problems.add(String.format("%s %s: never reaches below the body (y%.1f against a"
                        + " body bottom of y%.1f) — it holds nothing up", model, leg, low,
                        bodyBottom));
            }
            // A spider's knees ride above its back; a leg being the tallest thing on the animal is
            // what happens when a segment has swung past vertical.
            if (high > torsoTop) {
                problems.add(String.format("%s %s: rises to y%.1f, higher than anything on the body"
                        + " (y%.1f) — it is pointing at the sky", model, leg, high, torsoTop));
            }
        }
        return problems;
    }

    /**
     * Every rig here is an animal at rest, so its two flanks are the same pose reflected.
     *
     * <p>Nothing above catches a rig where they are not: each leg still reaches the floor and each
     * chain still holds together while one side fans out and the other folds into a bundle of legs
     * leaving a single point. The splay that shipped that way was one Y rotation per leg written
     * identically on both sides — and because the loader negates Y, identical is exactly what makes
     * them differ. Symmetry is checked after the transform for the same reason the rest of this
     * class is: in the file the wrong numbers look right.</p>
     */
    @Test
    void everyRigIsSymmetricAtRest() {
        List<String> problems = new ArrayList<>();
        for (GeoEnemyVariant variant : GeoEnemyVariant.all()) {
            Map<String, double[][]> extents = extents(variant.model());
            for (String bone : new TreeSet<>(extents.keySet())) {
                String other = partner(bone);
                if (other == null || !extents.containsKey(other)) {
                    continue;
                }
                double[][] mine = extents.get(bone);
                double[][] theirs = extents.get(other);
                double gap = 0;
                for (int a = 0; a < 3; a++) {
                    // Reflecting swaps which corner is the minimum in x, and leaves y and z alone.
                    double min = a == 0 ? -mine[1][0] : mine[0][a];
                    double max = a == 0 ? -mine[0][0] : mine[1][a];
                    gap = Math.max(gap, Math.max(Math.abs(min - theirs[0][a]),
                            Math.abs(max - theirs[1][a])));
                }
                if (gap > MIRROR_TOLERANCE) {
                    problems.add(String.format("%s %s and %s are not mirror images — %.1f apart at"
                            + " the worst corner; the two sides are in different poses",
                            variant.model(), bone, other, gap));
                }
            }
        }
        assertTrue(problems.isEmpty(), "rig symmetry:\n" + String.join("\n", problems));
    }

    /** The bone on the other flank, or null for one on the centre line. */
    private static String partner(String bone) {
        if (bone.contains("_left")) {
            return bone.replace("_left", "_right");
        }
        return bone.contains("_l") ? bone.replaceFirst("_l", "_r") : null;
    }

    private static boolean isLeg(String bone) {
        return bone.startsWith("leg") && !bone.equals("legs");
    }

    // --- the loader's transform ------------------------------------------------------------------

    /** An affine as {matrix rows..., translation}: a point goes to {@code m · p + t}. */
    private record Affine(double[][] m, double[] t) {}

    private static Affine identity() {
        return new Affine(new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, new double[]{0, 0, 0});
    }

    private static void walk(JsonArray bones, String name, Affine parent,
                             Map<String, double[][]> extents) {
        JsonObject bone = find(bones, name);
        double[] pivot = mirror(array(bone, "pivot"));
        Affine world = compose(parent, about(rotation(array(bone, "rotation")), pivot));

        if (bone.has("cubes")) {
            JsonArray cubes = bone.getAsJsonArray("cubes");
            for (int i = 0; i < cubes.size(); i++) {
                JsonObject cube = cubes.get(i).getAsJsonObject();
                double[] origin = array(cube, "origin");
                double[] size = array(cube, "size");
                // The X mirror: the min corner becomes the negated max corner.
                double[] min = {-(origin[0] + size[0]), origin[1], origin[2]};
                Affine local = cube.has("rotation")
                        ? about(rotation(array(cube, "rotation")), mirror(array(cube, "pivot")))
                        : identity();
                for (int c = 0; c < 8; c++) {
                    double[] corner = {
                            min[0] + ((c & 1) == 0 ? 0 : size[0]),
                            min[1] + ((c & 2) == 0 ? 0 : size[1]),
                            min[2] + ((c & 4) == 0 ? 0 : size[2])};
                    double[] p = put(world, put(local, corner));
                    double[][] span = extents.computeIfAbsent(name,
                            k -> new double[][]{{1e9, 1e9, 1e9}, {-1e9, -1e9, -1e9}});
                    for (int a = 0; a < 3; a++) {
                        span[0][a] = Math.min(span[0][a], p[a]);
                        span[1][a] = Math.max(span[1][a], p[a]);
                    }
                }
            }
        }
        for (int i = 0; i < bones.size(); i++) {
            JsonObject child = bones.get(i).getAsJsonObject();
            if (child.has("parent") && child.get("parent").getAsString().equals(name)) {
                walk(bones, child.get("name").getAsString(), world, extents);
            }
        }
    }

    private static JsonObject find(JsonArray bones, String name) {
        for (int i = 0; i < bones.size(); i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            if (bone.get("name").getAsString().equals(name)) {
                return bone;
            }
        }
        throw new IllegalStateException("no bone " + name);
    }

    private static double[] array(JsonObject owner, String key) {
        if (!owner.has(key)) {
            return new double[]{0, 0, 0};
        }
        JsonArray a = owner.getAsJsonArray(key);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static double[] mirror(double[] p) {
        return new double[]{-p[0], p[1], p[2]};
    }

    /** X and Y negated, Z as authored, composed Rz·Ry·Rx. */
    private static double[][] rotation(double[] degrees) {
        double x = Math.toRadians(-degrees[0]);
        double y = Math.toRadians(-degrees[1]);
        double z = Math.toRadians(degrees[2]);
        return mul(rotZ(z), mul(rotY(y), rotX(x)));
    }

    private static double[][] rotX(double a) {
        return new double[][]{{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}};
    }

    private static double[][] rotY(double a) {
        return new double[][]{{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}};
    }

    private static double[][] rotZ(double a) {
        return new double[][]{{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    out[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return out;
    }

    private static double[] apply(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    private static Affine about(double[][] m, double[] pivot) {
        double[] moved = apply(m, pivot);
        return new Affine(m, new double[]{pivot[0] - moved[0], pivot[1] - moved[1],
                pivot[2] - moved[2]});
    }

    /** {@code outer} applied after {@code inner}. */
    private static Affine compose(Affine outer, Affine inner) {
        double[] t = apply(outer.m(), inner.t());
        return new Affine(mul(outer.m(), inner.m()), new double[]{
                t[0] + outer.t()[0], t[1] + outer.t()[1], t[2] + outer.t()[2]});
    }

    private static double[] put(Affine a, double[] p) {
        double[] r = apply(a.m(), p);
        return new double[]{r[0] + a.t()[0], r[1] + a.t()[1], r[2] + a.t()[2]};
    }

    private static String read(String resource) {
        try (InputStream in = RigGeometryTest.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }
}
