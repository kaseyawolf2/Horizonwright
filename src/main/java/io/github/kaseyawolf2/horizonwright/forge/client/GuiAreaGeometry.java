package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

/** Explicit rectangular or circular area geometry, independent of the work performed there. */
final class GuiAreaGeometry extends GuiReadableScreen {

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editors;
    private final CurrentRuntimeProvider runtime;
    private final NamedArea original;
    private boolean circle;
    private GuiTextField[] fields;
    private int left, top, dimension;
    private String message = "Circle = horizontal radius with separate bottom/top Y. Rectangle = two XYZ corners.";

    GuiAreaGeometry(GuiScreen parent, ProfileAssetEditorProvider editors, CurrentRuntimeProvider runtime,
        NamedArea original) {
        this.parent = parent;
        this.editors = editors;
        this.runtime = runtime;
        this.original = original;
        circle = original == null || original.isCircular();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        left = (width - 500) / 2;
        top = (height - 310) / 2;
        BasePosition a = original == null ? feet()
            : original.isCircular() ? original.getCenter() : original.getMinimum();
        BasePosition b = original == null ? a : original.getMaximum();
        dimension = a.getDimensionId();
        String[] defaults = { original == null ? "new-area" : original.getDisplayName(), "" + a.getX(), "" + a.getY(),
            "" + a.getZ(), "" + b.getX(), "" + b.getY(), "" + b.getZ(),
            original != null && original.isCircular() ? "" + original.getRadius() : "5",
            "" + (original == null ? a.getY()
                : original.getMinimum()
                    .getY()),
            "" + b.getY() };
        if (fields != null) for (int i = 0; i < fields.length; i++) defaults[i] = fields[i].getText();
        fields = new GuiTextField[10];
        for (int i = 0; i < 10; i++) {
            int x = i == 0 ? 110 : 50 + ((i - 1) % 3) * 115;
            int y = i == 0 ? 44 : i <= 3 ? 100 : i <= 6 ? 152 : 204;
            fields[i] = readableField(fontRendererObj, left + x, top + y, i == 0 ? 250 : 90, 18);
            fields[i].setText(defaults[i]);
        }
        buttonList.add(
            new GuiHorizonwrightButton(1, left + 18, top + 70, 464, 20, circle ? "Shape: Circle" : "Shape: Rectangle"));
        buttonList.add(new GuiHorizonwrightButton(2, left + 405, top + 100, 77, 20, "My feet"));
        buttonList.add(new GuiHorizonwrightButton(3, left + 405, top + 152, 77, 20, "My feet"));
        buttonList.add(new GuiHorizonwrightButton(4, left + 18, top + 266, 340, 20, "Save geometry"));
        buttonList.add(new GuiHorizonwrightButton(0, left + 402, top + 266, 80, 20, "Back"));
        visibility();
    }

    private void visibility() {
        for (int i = 4; i <= 9; i++) {
            boolean visible = i <= 6 ? !circle : circle;
            fields[i].setVisible(visible);
            fields[i].setEnabled(visible);
            if (!visible) fields[i].setFocused(false);
        }
        for (Object obj : buttonList) if (((GuiButton) obj).id == 3) ((GuiButton) obj).visible = !circle;
    }

    private BasePosition feet() {
        if (mc.thePlayer == null || mc.theWorld == null) throw new IllegalStateException("Join the world first");
        return new BasePosition(
            mc.theWorld.provider.dimensionId,
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ));
    }

    private int value(int i) {
        return Integer.parseInt(
            fields[i].getText()
                .trim());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == 0) {
                mc.displayGuiScreen(parent);
                return;
            }
            if (button.id == 1) {
                circle = !circle;
                button.displayString = circle ? "Shape: Circle" : "Shape: Rectangle";
                visibility();
            }
            if (button.id == 2 || button.id == 3) {
                BasePosition p = feet();
                dimension = p.getDimensionId();
                int start = button.id == 2 ? 1 : 4;
                fields[start].setText("" + p.getX());
                fields[start + 1].setText("" + p.getY());
                fields[start + 2].setText("" + p.getZ());
            }
            if (button.id == 4) {
                String name = fields[0].getText()
                    .trim(), id = original == null ? name : original.getId();
                BasePosition a = new BasePosition(dimension, value(1), value(2), value(3));
                NamedArea area = circle ? NamedArea.circle(id, name, a, value(7), value(8), value(9))
                    : new NamedArea(id, name, a, new BasePosition(dimension, value(4), value(5), value(6)));
                if (original != null) area = area.withSettings(original.getKind(), original.getStorageId());
                io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor editor = editors
                    .getCurrentProfileAssetEditor()
                    .orElseThrow(() -> new IllegalStateException("Join the bound world first"));
                if (original == null) for (NamedArea saved : editor.load()
                    .getNamedAreas())
                    if (saved.getId()
                        .equals(id))
                        throw new IllegalArgumentException("Name already exists; edit the saved area instead");
                editor.apply(ProfileAssetUpdate.ofArea(area));
                mc.displayGuiScreen(new GuiAreaSettings(parent, editors, runtime, area));
            }
        } catch (RuntimeException failure) {
            message = "Not saved: " + failure.getMessage();
        }
    }

    @Override
    protected void drawContents(int x, int y, float ticks) {
        drawDefaultBackground();
        drawRect(left, top, left + 500, top + 310, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Area geometry", width / 2, top + 14, 0xFFF0C674);
        drawString(fontRendererObj, "Name", left + 18, top + 49, 0xFFB8C8DE);
        drawString(fontRendererObj, circle ? "Center XYZ" : "Corner 1 XYZ", left + 18, top + 91, 0xFFB8C8DE);
        if (!circle) drawString(fontRendererObj, "Corner 2 XYZ", left + 18, top + 139, 0xFFB8C8DE);
        else {
            drawString(fontRendererObj, "Radius", left + 50, top + 192, 0xFFB8C8DE);
            drawString(fontRendererObj, "Bottom Y", left + 165, top + 192, 0xFFB8C8DE);
            drawString(fontRendererObj, "Top Y", left + 280, top + 192, 0xFFB8C8DE);
        }
        for (GuiTextField field : fields) field.drawTextBox();
        drawParagraph(message, left + 18, top + 235, 464, 26, 0xFFB8C8DE);
        super.drawContents(x, y, ticks);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
        else for (GuiTextField field : fields) field.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        for (GuiTextField field : fields) field.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields) field.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
