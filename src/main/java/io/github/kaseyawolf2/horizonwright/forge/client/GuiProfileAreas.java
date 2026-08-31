package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Optional;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChunkCoordinates;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;

/** Nontechnical two-corner named work-area editor for farms, pens, and later bounded jobs. */
public final class GuiProfileAreas extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int FIRST_BUTTON = 2;
    private static final int SECOND_BUTTON = 3;
    private static final int SAVE_BUTTON = 4;

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editorProvider;
    private final ProfileAreaCapture capture = new ProfileAreaCapture();
    private GuiTextField areaId;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Stand at each opposite corner and capture your feet position.";

    public GuiProfileAreas(GuiScreen parent, ProfileAssetEditorProvider editorProvider) {
        if (parent == null || editorProvider == null) {
            throw new IllegalArgumentException("parent and editorProvider are required");
        }
        this.parent = parent;
        this.editorProvider = editorProvider;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(440, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 240) / 2);
        areaId = new GuiTextField(fontRendererObj, left + 132, top + 52, 180, 18);
        areaId.setMaxStringLength(48);
        areaId.setText("north-field");
        buttonList.add(new GuiButton(FIRST_BUTTON, left + 18, top + 88, 190, 20, "Capture corner 1 here"));
        buttonList.add(new GuiButton(SECOND_BUTTON, left + 232, top + 88, 190, 20, "Capture corner 2 here"));
        buttonList.add(new GuiButton(SAVE_BUTTON, left + 18, top + 166, panelWidth - 36, 22, "Save work area"));
        buttonList.add(new GuiButton(BACK_BUTTON, left + panelWidth - 82, top + 204, 70, 20, "Back"));
        refreshCount();
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
            if (button.id == FIRST_BUTTON) {
                capture.recordFirst(currentPosition());
                status = "Corner 1 captured. Walk to the opposite corner.";
            } else if (button.id == SECOND_BUTTON) {
                capture.recordSecond(currentPosition());
                status = "Corner 2 captured. Review both positions, then save.";
            } else if (button.id == SAVE_BUTTON) {
                save();
            }
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void save() {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("join the bound world before saving an area"));
        NamedArea area = capture.build(areaId.getText());
        ProfileEnvelope saved = editor.apply(ProfileAssetUpdate.ofArea(area));
        status = "Saved '" + area.getId()
            + "'. "
            + saved.getNamedAreas()
                .size()
            + " work area(s) in this world.";
    }

    private BasePosition currentPosition() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null) {
            throw new IllegalStateException("join the bound world before capturing a corner");
        }
        ChunkCoordinates position = mc.thePlayer.getPlayerCoordinates();
        return new BasePosition(mc.theWorld.provider.dimensionId, position.posX, position.posY, position.posZ);
    }

    private void refreshCount() {
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) return;
        try {
            int count = editor.get()
                .load()
                .getNamedAreas()
                .size();
            status = count + " saved work area(s). Capture both corners to add or replace one.";
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    @Override
    public void updateScreen() {
        areaId.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        areaId.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        areaId.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 240, 0xE010141B);
        drawCenteredString(fontRendererObj, "Named work areas", width / 2, top + 15, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Reusable boundaries for farms and animal pens",
            width / 2,
            top + 30,
            0xFF8FAAD0);
        drawString(fontRendererObj, "Area name", left + 18, top + 58, 0xFFE0E0E0);
        drawString(
            fontRendererObj,
            "Corner 1: " + truncate(capture.firstSummary(), 48),
            left + 18,
            top + 120,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            "Corner 2: " + truncate(capture.secondSummary(), 48),
            left + 18,
            top + 138,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(status, 66),
            left + 18,
            top + 152,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        areaId.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }
}
