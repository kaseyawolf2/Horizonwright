package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Optional;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.FarmTask;

/** Nontechnical two-corner named work-area editor for farms, pens, and later bounded jobs. */
public final class GuiProfileAreas extends GuiScreen {

    private static final ProfileAreaCaptureDrafts CAPTURE_DRAFTS = new ProfileAreaCaptureDrafts();

    private static final int BACK_BUTTON = 1;
    private static final int FIRST_BUTTON = 2;
    private static final int SECOND_BUTTON = 3;
    private static final int SAVE_BUTTON = 4;
    private static final int QUEUE_FARM_BUTTON = 5;
    private static final int SCHEDULE_FARM_BUTTON = 6;
    private static final int SAVED_AREAS_BUTTON = 7;
    private static final int CLOSE_BUTTON = 8;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editorProvider;
    private ProfileAreaCapture capture = new ProfileAreaCapture();
    private String captureProfileId;
    private GuiTextField areaId;
    private GuiTextField seedReserve;
    private GuiTextField intervalMinutes;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Stand at each opposite corner and capture your feet position.";

    public GuiProfileAreas(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider editorProvider) {
        if (parent == null || runtimeProvider == null || editorProvider == null) {
            throw new IllegalArgumentException("parent, runtimeProvider, and editorProvider are required");
        }
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
        this.editorProvider = editorProvider;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(440, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 310) / 2);
        areaId = new GuiTextField(fontRendererObj, left + 100, top + 52, 172, 18);
        areaId.setMaxStringLength(48);
        areaId.setText("north-field");
        seedReserve = new GuiTextField(fontRendererObj, left + 370, top + 52, 52, 18);
        seedReserve.setMaxStringLength(8);
        seedReserve.setText("2");
        intervalMinutes = new GuiTextField(fontRendererObj, left + 370, top + 78, 52, 18);
        intervalMinutes.setMaxStringLength(8);
        intervalMinutes.setText("30");
        restoreCaptureDraft();
        buttonList
            .add(new GuiHorizonwrightButton(FIRST_BUTTON, left + 18, top + 110, 190, 20, "Capture corner 1 here"));
        buttonList
            .add(new GuiHorizonwrightButton(SECOND_BUTTON, left + 232, top + 110, 190, 20, "Capture corner 2 here"));
        buttonList
            .add(new GuiHorizonwrightButton(SAVE_BUTTON, left + 18, top + 188, panelWidth - 36, 22, "Save work area"));
        buttonList.add(
            new GuiHorizonwrightButton(
                QUEUE_FARM_BUTTON,
                left + 18,
                top + 216,
                panelWidth - 36,
                22,
                "Queue one farm pass for this area"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SCHEDULE_FARM_BUTTON,
                left + 18,
                top + 244,
                panelWidth - 36,
                22,
                "Schedule recurring farm passes"));
        buttonList.add(new GuiHorizonwrightButton(CLOSE_BUTTON, left + 18, top + 278, 70, 20, "Close"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SAVED_AREAS_BUTTON,
                left + (panelWidth - 100) / 2,
                top + 278,
                100,
                20,
                "Saved areas"));
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 82, top + 278, 70, 20, "Back"));
        refreshCount();
        if (capture.hasFirst() || capture.hasSecond()) {
            status = "Corner draft retained while the page was closed. Capture the remaining corner.";
        }
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
        if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == SAVED_AREAS_BUTTON) {
            mc.displayGuiScreen(new GuiSavedAreas(this, editorProvider, runtimeProvider));
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
            } else if (button.id == QUEUE_FARM_BUTTON) {
                queueFarmPass();
            } else if (button.id == SCHEDULE_FARM_BUTTON) {
                scheduleFarmPasses();
            }
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void scheduleFarmPasses() {
        if (mc == null || mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        String plotId = ProfileAssetInput.stableId(areaId.getText(), "area name");
        requireSavedArea(plotId);
        int reserve = ProfileAssetInput.nonNegativeInteger(seedReserve.getText(), "minimum seed reserve");
        int minutes = ProfileAssetInput.positiveInteger(intervalMinutes.getText(), "farm interval minutes");
        long intervalMillis = Math.multiplyExact((long) minutes, 60_000L);
        String scheduleId = "farm-" + plotId;
        HorizonwrightRuntime runtime = CurrentRuntimeUiResolver.resolve(runtimeProvider)
            .getRuntime();
        runtime.scheduleFarm(scheduleId, plotId, reserve, intervalMillis);
        status = "Scheduled '" + scheduleId + "' every " + minutes + " connected minute(s).";
    }

    private void queueFarmPass() {
        if (mc == null || mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        String plotId = ProfileAssetInput.stableId(areaId.getText(), "area name");
        requireSavedArea(plotId);
        int reserve = ProfileAssetInput.nonNegativeInteger(seedReserve.getText(), "minimum seed reserve");
        String taskId = "farm-" + plotId + "-" + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld);
        HorizonwrightRuntime runtime = CurrentRuntimeUiResolver.resolve(runtimeProvider)
            .getRuntime();
        TaskSnapshot submitted = runtime.submitFarm(FarmTask.finitePass(taskId, plotId, reserve));
        status = "Queued '" + submitted.getSpec()
            .getId() + "' with seed reserve " + reserve + ".";
    }

    private void requireSavedArea(String plotId) {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
        for (NamedArea area : editor.load()
            .getNamedAreas()) {
            if (area.getId()
                .equals(plotId)) return;
        }
        throw new IllegalStateException("save work area '" + plotId + "' first");
    }

    private void save() {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("join the bound world before saving an area"));
        NamedArea area = capture.build(areaId.getText());
        ProfileEnvelope saved = editor.apply(ProfileAssetUpdate.ofArea(area));
        captureProfileId = saved.getIdentity()
            .getProfileId();
        CAPTURE_DRAFTS.clear(captureProfileId);
        capture = CAPTURE_DRAFTS.forProfile(captureProfileId);
        status = "Saved '" + area.getId()
            + "'. "
            + saved.getNamedAreas()
                .size()
            + " work area(s) in this world.";
    }

    private void restoreCaptureDraft() {
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) return;
        captureProfileId = editor.get()
            .load()
            .getIdentity()
            .getProfileId();
        capture = CAPTURE_DRAFTS.forProfile(captureProfileId);
    }

    private BasePosition currentPosition() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null) {
            throw new IllegalStateException("join the bound world before capturing a corner");
        }
        return new BasePosition(
            mc.theWorld.provider.dimensionId,
            MathHelper.floor_double(mc.thePlayer.posX),
            MathHelper.floor_double(mc.thePlayer.posY) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
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
        seedReserve.updateCursorCounter();
        intervalMinutes.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        areaId.textboxKeyTyped(character, keyCode);
        seedReserve.textboxKeyTyped(character, keyCode);
        intervalMinutes.textboxKeyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        areaId.mouseClicked(mouseX, mouseY, mouseButton);
        seedReserve.mouseClicked(mouseX, mouseY, mouseButton);
        intervalMinutes.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 310, 0xE010141B);
        drawCenteredString(fontRendererObj, "Named work areas", width / 2, top + 15, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Reusable boundaries for farms and animal pens",
            width / 2,
            top + 30,
            0xFF8FAAD0);
        drawString(fontRendererObj, "Area name", left + 18, top + 58, 0xFFE0E0E0);
        drawString(fontRendererObj, "Seed reserve", left + 292, top + 58, 0xFFE0E0E0);
        drawString(fontRendererObj, "Every minutes", left + 284, top + 84, 0xFFE0E0E0);
        drawString(
            fontRendererObj,
            "Corner 1: " + truncate(capture.firstSummary(), 48),
            left + 18,
            top + 142,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            "Corner 2: " + truncate(capture.secondSummary(), 48),
            left + 18,
            top + 160,
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(status, 66),
            left + 18,
            top + 174,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        areaId.drawTextBox();
        seedReserve.drawTextBox();
        intervalMinutes.drawTextBox();
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
