package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.chess.ChessBoardEntity;
import net.get900.pixelpirates.homestead.chess.ChessRules;
import net.get900.pixelpirates.homestead.chess.GiantChessBlock;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The pieces on a chess board, in 3D, and the last move ANIMATED: the piece glides from its square to the new one
 * (a knight hops, everything else lifts a little), a taken piece sinks and shrinks away, a castling rook slides too.
 * Pieces are built from boxes in "square units" (a square = 16): on the TABLE that's scaled to 2 px a square, on GIANT
 * CHESS a square is a whole block (the king stands ~1.4 blocks tall). Ivory and ebony textures from gen_chess_assets.py.
 */
@Environment(EnvType.CLIENT)
public class ChessRenderer implements BlockEntityRenderer<ChessBoardEntity> {
    private static final Identifier IVORY = new Identifier("pixelpirates", "textures/entity/chess_ivory.png");
    private static final Identifier EBONY = new Identifier("pixelpirates", "textures/entity/chess_ebony.png");
    private static ModelPart[] PIECES;
    private static final int ANIM = 10;                                               // ticks a move takes

    public ChessRenderer(BlockEntityRendererFactory.Context ctx) { if (PIECES == null) PIECES = build(); }

    /** Boxes {x, y, z, w, h, d} per piece (P N B R Q K), centred, standing on y 0, in square units. */
    private static final float[][][] SHAPES = {
            {{-5, 0, -5, 10, 2, 10}, {-3, 2, -3, 6, 5, 6}, {-2, 7, -2, 4, 1, 4}, {-3, 8, -3, 6, 4, 6}},                                   // pawn
            {{-5, 0, -5, 10, 2, 10}, {-3, 2, -3, 6, 6, 6}, {-3, 8, -5, 6, 5, 7}, {-2, 9, -8, 4, 3, 3}, {-2, 13, -1, 1, 2, 1}, {1, 13, -1, 1, 2, 1},
                    {-1, 6, 2, 2, 5, 2}},                                                                                                 // knight (faces -z)
            {{-5, 0, -5, 10, 2, 10}, {-3, 2, -3, 6, 8, 6}, {-4, 10, -4, 8, 1, 8}, {-2.5f, 11, -2.5f, 5, 5, 5}, {-1, 16, -1, 2, 2, 2}},     // bishop
            {{-5, 0, -5, 10, 2, 10}, {-4, 2, -4, 8, 9, 8}, {-5, 11, -5, 10, 2, 10}, {-5, 13, -5, 3, 2, 3}, {2, 13, -5, 3, 2, 3},
                    {-5, 13, 2, 3, 2, 3}, {2, 13, 2, 3, 2, 3}},                                                                           // rook
            {{-6, 0, -6, 12, 2, 12}, {-4, 2, -4, 8, 10, 8}, {-5, 12, -5, 10, 1, 10}, {-3, 13, -3, 6, 3, 6}, {-3, 16, -3, 1, 2, 1},
                    {2, 16, -3, 1, 2, 1}, {-3, 16, 2, 1, 2, 1}, {2, 16, 2, 1, 2, 1}, {-1, 16, -1, 2, 3, 2}},                               // queen
            {{-6, 0, -6, 12, 2, 12}, {-4, 2, -4, 8, 11, 8}, {-5, 13, -5, 10, 1, 10}, {-3, 14, -3, 6, 3, 6}, {-1, 17, -1, 2, 5, 2},
                    {-2.5f, 19, -1, 5, 1.5f, 2}}};                                                                                        // king

    private static ModelPart[] build() {
        ModelPart[] out = new ModelPart[6];
        for (int i = 0; i < 6; i++) {
            ModelData md = new ModelData();
            ModelPartBuilder b = ModelPartBuilder.create();
            for (float[] c : SHAPES[i]) b.uv(0, 0).cuboid(c[0], c[1], c[2], c[3], c[4], c[5]);
            md.getRoot().addChild("p", b, ModelTransform.NONE);
            out[i] = TexturedModelData.of(md, 64, 64).createModel().getChild("p");
        }
        return out;
    }

    @Override
    public boolean rendersOutsideBoundingBox(ChessBoardEntity be) { return be.giant(); }

    @Override
    public int getRenderDistance() { return 96; }

    @Override
    public void render(ChessBoardEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
        if (be.getWorld() == null) return;
        boolean giant = be.giant();
        Direction facing = be.getCachedState().get(FurnitureBlock.FACING);
        float t = be.lastMove < 0 ? 1 : MathHelper.clamp((be.getWorld().getTime() + tickDelta - be.moveTime) / ANIM, 0, 1);
        float ease = t * t * (3 - 2 * t);
        int mf = be.lastMove < 0 ? -1 : ChessRules.from(be.lastMove), mt = be.lastMove < 0 ? -1 : ChessRules.to(be.lastMove);
        int rookFrom = -1, rookTo = -1;
        if (mt >= 0 && Math.abs(be.game.sq[mt]) == ChessRules.K && Math.abs(mt - mf) == 2) { rookFrom = mt > mf ? mf + 3 : mf - 4; rookTo = mt > mf ? mf + 1 : mf - 1; }
        for (int s = 0; s < 64; s++) {
            int p = be.game.sq[s];
            if (p == 0) continue;
            float[] at = place(be, giant, facing, s);
            float lift = 0;
            if (t < 1 && (s == mt || s == rookTo)) {                                    // gliding in from where it was
                float[] from = place(be, giant, facing, s == mt ? mf : rookFrom);
                at = new float[]{MathHelper.lerp(ease, from[0], at[0]), MathHelper.lerp(ease, from[1], at[1]), MathHelper.lerp(ease, from[2], at[2])};
                boolean knight = Math.abs(p) == ChessRules.N && s == mt;
                lift = (float) Math.sin(Math.PI * t) * (knight ? 0.9f : 0.25f) * unit(giant) * 16;
            }
            piece(be, m, v, giant, facing, p, at, lift, 1f, light);
        }
        if (t < 0.7f && be.captured != 0 && be.capturedAt >= 0) {                        // the taken piece sinking away
            float k = 1 - t / 0.7f;
            float[] at = place(be, giant, facing, be.capturedAt);
            piece(be, m, v, giant, facing, be.captured, at, -(1 - k) * 0.4f * unit(giant) * 16, k, light);
        }
    }

    private static float unit(boolean giant) { return giant ? 1 / 16f : 1 / 128f; }

    /** Where square s's centre is, relative to the block entity (x, y of the board's top, z). */
    private static float[] place(ChessBoardEntity be, boolean giant, Direction facing, int s) {
        int f = s & 7, r = s >> 3;
        if (giant) {
            BlockPos q = GiantChessBlock.square(be.getPos(), facing, f, r).subtract(be.getPos());
            return new float[]{q.getX() + 0.5f, 0f, q.getZ() + 0.5f};
        }
        // the table (model faces north = white's side at -z): a-file at +x, rank 1 at the north edge
        float lx = 1 - (f * 2 + 1) / 16f, lz = (r * 2 + 1) / 16f;
        float cx = lx - 0.5f, cz = lz - 0.5f;
        float rot = (float) Math.toRadians(-facing.asRotation() + 180);
        float c = MathHelper.cos(rot), sn = MathHelper.sin(rot);
        return new float[]{0.5f + cx * c + cz * sn, 15 / 16f, 0.5f - cx * sn + cz * c};
    }

    private static void piece(ChessBoardEntity be, MatrixStack m, VertexConsumerProvider v, boolean giant, Direction facing, int p, float[] at,
                              float lift, float scale, int light) {
        boolean white = p > 0;
        m.push();
        m.translate(at[0], at[1] + lift, at[2]);
        // a knight looks across the board: on the table white sits at the model's -z side, on the giant set at the pedestal
        float face = -facing.asRotation() + 180 + (giant ? (white ? 0 : 180) : (white ? 180 : 0));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face));
        float u = unit(giant) * 16 * scale;
        m.scale(u, u, u);
        int lit = giant && be.getWorld() != null ? WorldRenderer.getLightmapCoordinates(be.getWorld(), BlockPos.ofFloored(be.getPos().getX() + at[0], be.getPos().getY() + at[1] + 0.2, be.getPos().getZ() + at[2])) : light;
        PIECES[Math.abs(p) - 1].render(m, v.getBuffer(RenderLayer.getEntityCutout(white ? IVORY : EBONY)), lit, OverlayTexture.DEFAULT_UV);
        m.pop();
    }
}
