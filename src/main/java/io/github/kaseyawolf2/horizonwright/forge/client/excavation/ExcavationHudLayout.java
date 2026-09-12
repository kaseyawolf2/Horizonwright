package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Editor and overlay share the same absolute screen coordinates, without model-view transforms. */
public final class ExcavationHudLayout {

    public final int width;
    public final int height;
    private final List<String> lines = new ArrayList<>();

    public ExcavationHudLayout(FontRenderer font, String text, int screenWidth, int screenHeight) {
        int wrap = Math.max(1, Math.min(280, screenWidth - 12));
        int widest = 0;
        int maxLines = Math.max(1, (screenHeight - 10) / 11);
        for (String line : text.split("\n")) for (Object wrapped : font.listFormattedStringToWidth(line, wrap)) {
            if (lines.size() >= maxLines) break;
            String value = wrapped.toString();
            lines.add(value);
            widest = Math.max(widest, font.getStringWidth(value));
        }
        width = Math.min(screenWidth, widest + 12);
        height = Math.min(screenHeight, lines.size() * 11 + 10);
    }

    public void draw(FontRenderer font, int x, int y, boolean editing) {
        Gui.drawRect(x, y, x + width, y + height, 0xB0101620);
        if (editing) {
            Gui.drawRect(x, y, x + width, y + 1, 0xFF70DFFF);
            Gui.drawRect(x, y + height - 1, x + width, y + height, 0xFF70DFFF);
            Gui.drawRect(x, y, x + 1, y + height, 0xFF70DFFF);
            Gui.drawRect(x + width - 1, y, x + width, y + height, 0xFF70DFFF);
        }
        int lineY = y + 5;
        for (String line : lines) {
            font.drawStringWithShadow(line, x + 6, lineY, 0xBDEBFF);
            lineY += 11;
        }
    }
}
