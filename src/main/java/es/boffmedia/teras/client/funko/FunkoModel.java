package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Faithful Java reproduction of {@code funko3.bbmodel}.
 *
 * <p>The Blockbench source maps every cube face onto the standard 64x64 player-skin
 * regions (head south = skin face, body front = 20,20, ...), so binding <em>any</em>
 * player skin (or any PNG using the player-skin layout) textures the statue correctly.
 * UVs are kept in the model's 0-16 space and normalised by {@link #UV_SIZE}; this is
 * resolution independent, so the same coordinates work for 64x64 skins.</p>
 *
 * <p>Geometry/winding mirror vanilla {@code ModelPart.Cube} exactly (no inflation, no
 * mirroring) so lighting and culling behave like a normal model part.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoModel {

    private static final float UV_SIZE = 16.0F;

    /** Opaque white, ARGB. 1.16.5 took r/g/b/a floats here, but every call site passed white. */
    private static final int WHITE = 0xFFFFFFFF;

    private static final List<Quad> QUADS = new ArrayList<>();

    static {
        // cabeza (head)
        cube(6.5F, 4F, 6.25F, 10.5F, 8F, 10.25F,
                face(Direction.NORTH, 6, 2, 8, 4, 0),
                face(Direction.EAST, 4, 2, 6, 4, 0),
                face(Direction.SOUTH, 2, 2, 4, 4, 0),
                face(Direction.WEST, 0, 2, 2, 4, 0),
                face(Direction.UP, 2, 0, 4, 2, 180),
                face(Direction.DOWN, 4, 0, 6, 2, 180));
        // cuerpo (body)
        cube(7.5F, 2F, 7.5F, 9.5F, 4F, 9F,
                face(Direction.NORTH, 8, 5, 10, 8, 0),
                face(Direction.EAST, 7, 5, 8, 8, 0),
                face(Direction.SOUTH, 5, 5, 7, 8, 0),
                face(Direction.WEST, 4, 5, 5, 8, 0),
                face(Direction.UP, 5, 4, 7, 5, 180),
                face(Direction.DOWN, 7, 4, 9, 5, 180));
        // brazoDer (right arm)
        cube(6.5F, 2F, 7.5F, 7.5F, 4F, 9F,
                face(Direction.NORTH, 13, 5, 14, 8, 0),
                face(Direction.EAST, 12, 5, 13, 8, 0),
                face(Direction.SOUTH, 11, 5, 12, 8, 0),
                face(Direction.WEST, 10, 5, 11, 8, 0),
                face(Direction.UP, 11, 4, 12, 5, 180),
                face(Direction.DOWN, 12, 4, 13, 5, 0));
        // brazoIz (left arm)
        cube(9.5F, 2F, 7.5F, 10.5F, 4F, 9F,
                face(Direction.NORTH, 11, 13, 12, 16, 0),
                face(Direction.EAST, 10, 13, 11, 16, 0),
                face(Direction.SOUTH, 9, 13, 10, 16, 0),
                face(Direction.WEST, 8, 13, 9, 16, 0),
                face(Direction.UP, 9, 12, 10, 13, 180),
                face(Direction.DOWN, 10, 12, 11, 13, 0));
        // piernaDer (right leg)
        cube(7.5F, 0F, 7.5F, 8.5F, 2F, 9F,
                face(Direction.NORTH, 3, 5, 4, 8, 0),
                face(Direction.EAST, 2, 5, 3, 8, 0),
                face(Direction.SOUTH, 1, 5, 2, 8, 0),
                face(Direction.WEST, 0, 5, 1, 8, 0),
                face(Direction.UP, 1, 4, 2, 5, 180),
                face(Direction.DOWN, 2, 4, 3, 5, 180));
        // piernaIz (left leg)
        cube(8.5F, 0F, 7.5F, 9.5F, 2F, 9F,
                face(Direction.NORTH, 7, 13, 8, 16, 0),
                face(Direction.EAST, 6, 13, 7, 16, 0),
                face(Direction.SOUTH, 5, 13, 6, 16, 0),
                face(Direction.WEST, 4, 13, 5, 16, 0),
                face(Direction.UP, 5, 12, 6, 13, 180),
                face(Direction.DOWN, 6, 12, 7, 13, 180));
    }

    private FunkoModel() {
    }

    /** Renders the statue in model space (pixels, 0-16). The caller positions/rotates the matrix. */
    public static void render(PoseStack poseStack, VertexConsumer builder, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        for (Quad quad : QUADS) {
            for (int i = 0; i < 4; i++) {
                float[] p = quad.pos[i];
                builder.addVertex(pose, p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F)
                        .setColor(WHITE)
                        .setUv(quad.uv[i][0], quad.uv[i][1])
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(pose, quad.normal.x(), quad.normal.y(), quad.normal.z());
            }
        }
    }

    private static FaceDef face(Direction dir, float u1, float v1, float u2, float v2, int rotation) {
        return new FaceDef(dir, u1, v1, u2, v2, rotation);
    }

    private static void cube(float x0, float y0, float z0, float x1, float y1, float z1, FaceDef... faces) {
        for (FaceDef f : faces) {
            QUADS.add(buildQuad(x0, y0, z0, x1, y1, z1, f));
        }
    }

    private static Quad buildQuad(float x0, float y0, float z0, float x1, float y1, float z1, FaceDef f) {
        // Vertex order per face matches vanilla FaceInfo (the block-model baking convention),
        // so the texture renders upright with no scale(-1,-1,1) flip and a normal v-down skin layout.
        float[][] pos = switch (f.dir) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };

        // UVs assigned exactly as BlockFaceUV.getU/getV: index shifted by rotation, then
        // vertices 0,1 -> uMin and 0,3 -> vMin (vMin is the top row of the texture region).
        float uMin = f.u1 / UV_SIZE, vMin = f.v1 / UV_SIZE, uMax = f.u2 / UV_SIZE, vMax = f.v2 / UV_SIZE;
        int shift = (f.rotation % 360) / 90;
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) {
            int idx = (i + shift) & 3;
            float u = (idx == 0 || idx == 1) ? uMin : uMax;
            float v = (idx == 0 || idx == 3) ? vMin : vMax;
            uv[i] = new float[]{u, v};
        }

        return new Quad(pos, uv, f.dir.step());
    }

    private record FaceDef(Direction dir, float u1, float v1, float u2, float v2, int rotation) {
    }

    private record Quad(float[][] pos, float[][] uv, Vector3f normal) {
    }
}
