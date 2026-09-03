package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/** Seam-free dashboard button which is not limited by the 200-pixel vanilla widget texture. */
final class GuiHorizonwrightButton extends GuiButton {

    GuiHorizonwrightButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hovered = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        int border = !enabled ? 0xFF3C424B : hovered ? 0xFFF0C674 : 0xFF737B86;
        int fill = !enabled ? 0xCC222831 : hovered ? 0xEE46505D : 0xEE343B45;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, border);
        drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, fill);

        FontRenderer font = minecraft.fontRenderer;
        String text = fit(font, displayString, width - 8);
        int color = !enabled ? 0xFF777777 : hovered ? 0xFFFFFFA0 : 0xFFE0E0E0;
        drawCenteredString(font, text, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }

    private static String fit(FontRenderer font, String value, int maximumWidth) {
        if (font.getStringWidth(value) <= maximumWidth) return value;
        String ellipsis = "...";
        int contentWidth = Math.max(0, maximumWidth - font.getStringWidth(ellipsis));
        return font.trimStringToWidth(value, contentWidth) + ellipsis;
    }
}
