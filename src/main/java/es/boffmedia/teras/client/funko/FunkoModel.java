package es.boffmedia.teras.client.funko;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
 * <p>Geometry/winding mirror vanilla {@code ModelRenderer.ModelBox} exactly (no
 * inflation, no mirroring) so lighting and culling behave like a normal model part.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FunkoModel {

    private static final float UV_SIZE = 16.0F;

    private static final List<BakedQuad> QUADS = new ArrayList<>();

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
    public static void render(MatrixStack matrixStack, IVertexBuilder builder, int packedLight, int packedOverlay,
                              float r, float g, float b, float a) {
        Matrix4f pose = matrixStack.last().pose();
        Matrix3f normal = matrixStack.last().normal();
        for (BakedQuad quad : QUADS) {
            Vector3f n = quad.normal.copy();
            n.transform(normal);
            for (int i = 0; i < 4; i++) {
                float[] p = quad.pos[i];
                Vector4f vertex = new Vector4f(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F, 1.0F);
                vertex.transform(pose);
                builder.vertex(vertex.x(), vertex.y(), vertex.z(), r, g, b, a,
                        quad.uv[i][0], quad.uv[i][1], packedOverlay, packedLight, n.x(), n.y(), n.z());
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

    private static BakedQuad buildQuad(float x0, float y0, float z0, float x1, float y1, float z1, FaceDef f) {
        // Vertex order per face matches vanilla FaceDirection (the block-model baking convention),
        // so the texture renders upright with no scale(-1,-1,1) flip and a normal v-down skin layout.
        float[][] pos;
        switch (f.dir) {
            case DOWN:
                pos = new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                break;
            case UP:
                pos = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                break;
            case NORTH:
                pos = new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                break;
            case SOUTH:
                pos = new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                break;
            case WEST:
                pos = new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                break;
            case EAST:
            default:
                pos = new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
                break;
        }

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

        return new BakedQuad(pos, uv, f.dir.step());
    }

    private static final class FaceDef {
        final Direction dir;
        final float u1;
        final float v1;
        final float u2;
        final float v2;
        final int rotation;

        FaceDef(Direction dir, float u1, float v1, float u2, float v2, int rotation) {
            this.dir = dir;
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
            this.rotation = rotation;
        }
    }

    private static final class BakedQuad {
        final float[][] pos;
        final float[][] uv;
        final Vector3f normal;

        BakedQuad(float[][] pos, float[][] uv, Vector3f normal) {
            this.pos = pos;
            this.uv = uv;
            this.normal = normal;
        }
    }
}
