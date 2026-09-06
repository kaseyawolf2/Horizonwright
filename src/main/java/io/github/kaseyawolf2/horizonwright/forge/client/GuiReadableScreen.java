package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Shared viewport and lossless text rendering for the operations screens. */
public abstract class GuiReadableScreen extends GuiScreen {

    private final List<GuiTextField> readableFields = new ArrayList<>();
    private final List<TextHit> textHits = new ArrayList<>();
    private float viewportScale = 1;
    private boolean drawingTooltip;
    private int tooltipOffset;
    private String lastTooltip = "";

    @Override
    public void setWorldAndResolution(Minecraft minecraft, int actualWidth, int actualHeight) {
        viewportScale = Math.min(1F, Math.min(actualWidth / 540F, actualHeight / 410F));
        super.setWorldAndResolution(
            minecraft,
            Math.round(actualWidth / viewportScale),
            Math.round(actualHeight / viewportScale));
    }

    @Override
    public void initGui() {
        readableFields.clear();
    }

    protected GuiTextField readableField(FontRenderer font, int x, int y, int fieldWidth, int fieldHeight) {
        GuiTextField field = new GuiTextField(font, x, y, fieldWidth, fieldHeight);
        readableFields.add(field);
        return field;
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        textHits.clear();
        GL11.glPushMatrix();
        GL11.glScalef(viewportScale, viewportScale, 1);
        try {
            drawContents(Math.round(mouseX / viewportScale), Math.round(mouseY / viewportScale), partialTicks);
        } finally {
            GL11.glPopMatrix();
        }
    }

    protected void drawContents(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Object candidate : buttonList) {
            GuiButton button = (GuiButton) candidate;
            if (button.visible
                && contains(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)
                && fontRendererObj.getStringWidth(button.displayString) > button.width - 8) {
                showText(button.displayString, mouseX, mouseY);
                return;
            }
        }
        for (GuiTextField field : readableFields) {
            if (field.getVisible()
                && contains(mouseX, mouseY, field.xPosition, field.yPosition, field.width, field.height)
                && fontRendererObj.getStringWidth(field.getText()) > field.width - 8) {
                showText(field.getText(), mouseX, mouseY);
                return;
            }
        }
        for (TextHit hit : textHits) {
            if (contains(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)) {
                showText(hit.text, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public void drawString(FontRenderer font, String text, int x, int y, int color) {
        if (text == null) return;
        int available = Math.max(4, width - 16 - x);
        for (GuiTextField field : readableFields) {
            if (field.getVisible() && field.xPosition > x
                && y + font.FONT_HEIGHT > field.yPosition
                && y < field.yPosition + field.height) {
                available = Math.min(available, Math.max(4, field.xPosition - x - 6));
            }
        }
        for (Object candidate : buttonList) {
            GuiButton button = (GuiButton) candidate;
            if (button.visible && button.xPosition > x
                && y + font.FONT_HEIGHT > button.yPosition
                && y < button.yPosition + button.height) {
                available = Math.min(available, Math.max(4, button.xPosition - x - 6));
            }
        }
        String fitted = fitText(font, text, available);
        super.drawString(font, fitted, x, y, color);
        if (!drawingTooltip && !fitted.equals(text)) textHits.add(new TextHit(text, x, y, available, font.FONT_HEIGHT));
    }

    @Override
    public void drawCenteredString(FontRenderer font, String text, int center, int y, int color) {
        int available = Math.max(4, 2 * Math.min(center - 16, width - center - 16));
        String fitted = fitText(font, text, available);
        super.drawCenteredString(font, fitted, center, y, color);
        if (!drawingTooltip && !fitted.equals(text)) {
            textHits.add(new TextHit(text, center - available / 2, y, available, font.FONT_HEIGHT));
        }
    }

    protected void drawParagraph(String text, int x, int y, int lineWidth, int maximumHeight, int color) {
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, lineWidth);
        int count = Math.min(lines.size(), Math.max(1, maximumHeight / fontRendererObj.FONT_HEIGHT));
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            if (i == count - 1 && count < lines.size()) line = fitText(fontRendererObj, line + " ...", lineWidth);
            super.drawString(fontRendererObj, line, x, y + i * fontRendererObj.FONT_HEIGHT, color);
        }
        if (count < lines.size()) textHits.add(new TextHit(text, x, y, lineWidth, count * fontRendererObj.FONT_HEIGHT));
    }

    private void showText(String text, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
    }

    @Override
    protected void drawHoveringText(List<String> lines, int mouseX, int mouseY, FontRenderer font) {
        String key = lines.toString();
        if (!key.equals(lastTooltip)) tooltipOffset = 0;
        lastTooltip = key;
        List<String> wrapped = new ArrayList<>();
        int tooltipWidth = Math.min(380, width - 40);
        for (String line : lines) wrapped.addAll(font.listFormattedStringToWidth(line, tooltipWidth));
        int capacity = Math.max(2, (height - 48) / 10);
        tooltipOffset = Math.max(0, Math.min(tooltipOffset, Math.max(0, wrapped.size() - capacity)));
        List<String> visible = new ArrayList<>(
            wrapped.subList(tooltipOffset, Math.min(wrapped.size(), tooltipOffset + capacity)));
        if (wrapped.size() > capacity)
            visible.add("Mouse wheel: more text (" + (tooltipOffset + 1) + "/" + wrapped.size() + ")");
        int widest = 0;
        for (String line : visible) widest = Math.max(widest, font.getStringWidth(line));
        int anchorX = Math.max(0, Math.min(mouseX, width - widest - 24));
        int anchorY = Math.max(18, Math.min(mouseY, height - visible.size() * 10 - 12));
        drawingTooltip = true;
        try {
            super.drawHoveringText(visible, anchorX, anchorY, font);
        } finally {
            drawingTooltip = false;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) tooltipOffset = Math.max(0, tooltipOffset + (wheel < 0 ? 3 : -3));
    }

    static String fitText(FontRenderer font, String text, int maximumWidth) {
        if (text == null) return "";
        if (font.getStringWidth(text) <= maximumWidth) return text;
        String suffix = font.trimStringToWidth("...", maximumWidth);
        return font.trimStringToWidth(text, Math.max(0, maximumWidth - font.getStringWidth(suffix))) + suffix;
    }

    private static boolean contains(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static final class TextHit {

        private final String text;
        private final int x, y, width, height;

        private TextHit(String text, int x, int y, int width, int height) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
