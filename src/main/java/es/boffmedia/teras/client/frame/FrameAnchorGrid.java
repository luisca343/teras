package es.boffmedia.teras.client.frame;

import es.boffmedia.teras.blockentity.FrameBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A 3×3 picker for the display's anchor — the point it grows from when resized. Columns are
 * horizontal (left/centre/right), rows are vertical drawn top-to-bottom but stored bottom-up
 * (row 0 = top = {@link FrameBlockEntity#ANCHOR_MAX}). The chosen cell is highlighted.
 */
@OnlyIn(Dist.CLIENT)
class FrameAnchorGrid extends AbstractWidget {

    interface OnPick {
        void pick(byte anchorH, byte anchorV);
    }

    private byte anchorH;
    private byte anchorV;
    private final OnPick onPick;

    FrameAnchorGrid(int x, int y, int size, byte anchorH, byte anchorV, OnPick onPick) {
        super(x, y, size, size, Component.translatable("gui.teras.frame_anchor"));
        this.anchorH = anchorH;
        this.anchorV = anchorV;
        this.onPick = onPick;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cell = getWidth() / 3;
        graphics.fill(getX(), getY(), getX() + cell * 3, getY() + cell * 3, 0xFF202020);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = getX() + col * cell;
                int cy = getY() + row * cell;
                boolean selected = col == anchorH && (2 - row) == anchorV;
                boolean hovered = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell;
                int fill = selected ? 0xFFFFFFFF : (hovered ? 0xFF666666 : 0xFF444444);
                graphics.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, fill);
            }
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int cell = getWidth() / 3;
        int col = Mth.clamp((int) ((mouseX - getX()) / cell), 0, 2);
        int row = Mth.clamp((int) ((mouseY - getY()) / cell), 0, 2);
        anchorH = (byte) col;
        anchorV = (byte) (2 - row);
        onPick.pick(anchorH, anchorV);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
