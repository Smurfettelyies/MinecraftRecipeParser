package de.nissk.recipeparser;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Kleiner 20x20 Button mit "RT"-Label der neben dem Inventar erscheint.
 */
public class RecipeTreeIconButton extends Button {

    private static final int W = 20;
    private static final int H = 20;

    private static final int BG     = 0xFF1e1e1e;
    private static final int BORDER = 0xFF7ec850;
    private static final int HOVER  = 0xFF2a3a2a;
    private static final int TEXT   = 0xFF7ec850;

    public RecipeTreeIconButton(int x, int y, OnPress onPress) {
        super(x, y, W, H, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY();
        int fill = isHovered() ? HOVER : BG;

        // Border
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, BORDER);
        // Fill
        g.fill(x, y, x + W, y + H, fill);
        // Label
        g.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                "RT",
                x + W / 2,
                y + H / 2 - 4,
                TEXT
        );
    }
}
