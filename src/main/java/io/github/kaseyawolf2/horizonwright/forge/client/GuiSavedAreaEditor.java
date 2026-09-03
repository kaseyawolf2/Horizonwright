package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;

/** Safe editor for one existing named area's display name and inclusive block bounds. */
public final class GuiSavedAreaEditor extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int SAVE_BUTTON = 2;
    private static final int FIRST_HERE_BUTTON = 3;
    private static final int SECOND_HERE_BUTTON = 4;

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editorProvider;
    private final NamedArea original;
    private GuiTextField displayName;
    private GuiTextField dimension;
    private GuiTextField firstX;
    private GuiTextField firstY;
    private GuiTextField firstZ;
    private GuiTextField secondX;
    private GuiTextField secondY;
    private GuiTextField secondZ;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Edit the inclusive bounds, or stand at a corner and capture your feet position.";

    public GuiSavedAreaEditor(GuiScreen parent, ProfileAssetEditorProvider editorProvider, NamedArea original) {
        if (parent == null || editorProvider == null || original == null) {
            throw new IllegalArgumentException("parent, editorProvider, and original area are required");
        }
        this.parent = parent;
        this.editorProvider = editorProvider;
        this.original = original;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(500, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 294) / 2);

        displayName = field(left + 116, top + 52, 188, original.getDisplayName());
        dimension = field(
            left + 408,
            top + 52,
            70,
            Integer.toString(
                original.getMinimum()
                    .getDimensionId()));
        firstX = field(
            left + 70,
            top + 104,
            64,
            Integer.toString(
                original.getMinimum()
                    .getX()));
        firstY = field(
            left + 180,
            top + 104,
            48,
            Integer.toString(
                original.getMinimum()
                    .getY()));
        firstZ = field(
            left + 274,
            top + 104,
            64,
            Integer.toString(
                original.getMinimum()
                    .getZ()));
        secondX = field(
            left + 70,
            top + 158,
            64,
            Integer.toString(
                original.getMaximum()
                    .getX()));
        secondY = field(
            left + 180,
            top + 158,
            48,
            Integer.toString(
                original.getMaximum()
                    .getY()));
        secondZ = field(
            left + 274,
            top + 158,
            64,
            Integer.toString(
                original.getMaximum()
                    .getZ()));

        buttonList.add(new GuiButton(FIRST_HERE_BUTTON, left + 350, top + 103, 128, 20, "Use my feet here"));
        buttonList.add(new GuiButton(SECOND_HERE_BUTTON, left + 350, top + 157, 128, 20, "Use my feet here"));
        buttonList.add(
            new GuiHorizonwrightButton(SAVE_BUTTON, left + 18, top + 232, panelWidth - 112, 22, "Save area changes"));
        buttonList.add(new GuiButton(BACK_BUTTON, left + panelWidth - 82, top + 233, 64, 20, "Back"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK_BUTTON) {
            mc.displayGuiScreen(parent);
            return;
        }
        try {
            if (button.id == FIRST_HERE_BUTTON) capture(firstX, firstY, firstZ);
            else if (button.id == SECOND_HERE_BUTTON) capture(secondX, secondY, secondZ);
            else if (button.id == SAVE_BUTTON) save();
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void capture(GuiTextField x, GuiTextField y, GuiTextField z) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null) {
            throw new IllegalStateException("join the bound world before capturing a corner");
        }
        dimension.setText(Integer.toString(mc.theWorld.provider.dimensionId));
        x.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posX)));
        y.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posY) - 1));
        z.setText(Integer.toString(MathHelper.floor_double(mc.thePlayer.posZ)));
        status = "Captured feet position. Review both corners, then save.";
    }

    private void save() {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
        int parsedDimension = integer(dimension.getText(), "dimension");
        BasePosition first = new BasePosition(
            parsedDimension,
            integer(firstX.getText(), "corner 1 X"),
            integer(firstY.getText(), "corner 1 Y"),
            integer(firstZ.getText(), "corner 1 Z"));
        BasePosition second = new BasePosition(
            parsedDimension,
            integer(secondX.getText(), "corner 2 X"),
            integer(secondY.getText(), "corner 2 Y"),
            integer(secondZ.getText(), "corner 2 Z"));
        String name = displayName.getText() == null ? ""
            : displayName.getText()
                .trim();
        if (name.isEmpty()) throw new IllegalArgumentException("display name must not be blank");
        editor.apply(ProfileAssetUpdate.ofArea(new NamedArea(original.getId(), name, first, second)));
        status = "Saved area '" + original.getId() + "'. Schedules using it remain connected.";
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields()) field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        for (GuiTextField field : fields()) field.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : fields()) field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 274, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Edit saved work area", width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(fontRendererObj, "Stable ID: " + original.getId(), width / 2, top + 29, 0xFF8FAAD0);
        label("Display name", left + 18, top + 58);
        label("Dimension", left + 338, top + 58);
        label("Corner 1", left + 18, top + 84);
        coordinateLabels(top + 110);
        label("Corner 2", left + 18, top + 138);
        coordinateLabels(top + 164);
        fontRendererObj.drawSplitString(
            status,
            left + 18,
            top + 194,
            panelWidth - 36,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void coordinateLabels(int y) {
        label("X", left + 54, y);
        label("Y", left + 164, y);
        label("Z", left + 258, y);
    }

    private void label(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFE0E0E0);
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(64);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { displayName, dimension, firstX, firstY, firstZ, secondX, secondY, secondZ };
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(field + " must be a whole number", failure);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
