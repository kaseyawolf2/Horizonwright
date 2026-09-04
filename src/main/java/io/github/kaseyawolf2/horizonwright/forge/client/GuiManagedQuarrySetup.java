package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Optional;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.excavation.ManagedQuarryConfiguration;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTaskSubmission;

/** Guided managed-quarry submission with explicit approved infrastructure materials. */
public final class GuiManagedQuarrySetup extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int SUBMIT_BUTTON = 2;
    private static final int SERVICES_BUTTON = 3;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editorProvider;
    private GuiTextField taskId;
    private GuiTextField radius;
    private GuiTextField bottomY;
    private GuiTextField topY;
    private GuiTextField rampMaterial;
    private GuiTextField lightMaterial;
    private GuiTextField fillerMaterial;
    private GuiTextField lightInterval;
    private GuiTextField loadoutId;
    private GuiTextField storageId;
    private GuiTextField stationId;
    private GuiTextField toolSlot;
    private GuiTextField workDamage;
    private GuiButton servicesButton;
    private boolean servicesEnabled = true;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "The quarry center is your current X/Z when you press Queue.";

    public GuiManagedQuarrySetup(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
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
        panelWidth = Math.min(500, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - 384) / 2);
        taskId = field(left + 138, top + 44, 120, "quarry-1");
        radius = field(left + 356, top + 44, 48, "8");
        bottomY = field(left + 138, top + 70, 48, Integer.toString(currentY() - 8));
        topY = field(left + 356, top + 70, 48, Integer.toString(currentY()));
        rampMaterial = field(left + 138, top + 112, 120, "minecraft:cobblestone");
        lightMaterial = field(left + 356, top + 112, 120, "minecraft:torch");
        fillerMaterial = field(left + 138, top + 138, 120, "minecraft:cobblestone");
        lightInterval = field(left + 356, top + 138, 48, "4");
        loadoutId = field(left + 138, top + 202, 120, "mining");
        storageId = field(left + 356, top + 202, 120, "ore-chest");
        stationId = field(left + 138, top + 228, 120, "tool-forge");
        toolSlot = field(left + 356, top + 228, 48, "0");
        workDamage = field(left + 138, top + 254, 48, "1");
        servicesButton = new GuiHorizonwrightButton(SERVICES_BUTTON, left + 282, top + 254, 194, 20, "Services: ON");
        buttonList.add(servicesButton);
        buttonList.add(
            new GuiHorizonwrightButton(
                SUBMIT_BUTTON,
                left + 18,
                top + 326,
                panelWidth - 36,
                22,
                "Queue managed quarry"));
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 82, top + 354, 70, 20, "Back"));
        populateSavedNames();
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
        if (button.id == SERVICES_BUTTON) {
            servicesEnabled = !servicesEnabled;
            servicesButton.displayString = servicesEnabled ? "Services: ON" : "Services: off";
            return;
        }
        if (button.id != SUBMIT_BUTTON) return;
        try {
            HorizonwrightRuntime runtime = CurrentRuntimeUiResolver.resolve(runtimeProvider)
                .getRuntime();
            if (mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null) {
                throw new IllegalStateException("join the bound world first");
            }
            int centerX = MathHelper.floor_double(mc.thePlayer.posX);
            int centerZ = MathHelper.floor_double(mc.thePlayer.posZ);
            String id = ProfileAssetInput.stableId(taskId.getText(), "task name");
            int parsedRadius = ProfileAssetInput.nonNegativeInteger(radius.getText(), "radius");
            int parsedBottom = integer(bottomY.getText(), "bottom Y");
            int parsedTop = integer(topY.getText(), "top Y");
            ManagedQuarryConfiguration configuration = new ManagedQuarryConfiguration(
                ManagedQuarryMaterialInput.requirePlaceableBlock(rampMaterial.getText(), "ramp block"),
                ManagedQuarryMaterialInput.requirePlaceableBlock(lightMaterial.getText(), "light block"),
                ManagedQuarryMaterialInput.requirePlaceableBlock(fillerMaterial.getText(), "fluid filler"),
                ProfileAssetInput.positiveInteger(lightInterval.getText(), "light interval"));
            TaskSpec spec;
            if (!servicesEnabled) {
                spec = ExcavationTaskSubmission.managedWithoutServices(
                    id,
                    mc.theWorld.provider.dimensionId,
                    centerX,
                    centerZ,
                    parsedRadius,
                    parsedBottom,
                    parsedTop,
                    configuration);
            } else {
                ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
                    .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
                spec = ExcavationTaskSubmission.managedWithServices(
                    editor.load(),
                    id,
                    mc.theWorld.provider.dimensionId,
                    centerX,
                    centerZ,
                    parsedRadius,
                    parsedBottom,
                    parsedTop,
                    configuration,
                    loadoutId.getText(),
                    storageId.getText(),
                    stationId.getText(),
                    ProfileAssetInput.inventorySlot(toolSlot.getText(), "tool slot"),
                    ProfileAssetInput.nonNegativeInteger(workDamage.getText(), "predicted work damage"));
            }
            TaskSnapshot submitted = runtime.submitExcavation(spec);
            status = "Queued '" + submitted.getSpec()
                .getId() + "' at X/Z " + centerX + "/" + centerZ + ".";
        } catch (RuntimeException failure) {
            status = "Nothing queued: " + safeMessage(failure);
        }
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
        drawRect(left, top, left + panelWidth, top + 384, 0xE010141B);
        drawCenteredString(fontRendererObj, "New managed quarry", width / 2, top + 10, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Retained ramp, approved lights, and fluid filler",
            width / 2,
            top + 25,
            0xFF8FAAD0);
        label("Task name", left + 18, top + 50);
        label("Radius (2-250)", left + 270, top + 50);
        label("Bottom Y", left + 18, top + 76);
        label("Top Y", left + 270, top + 76);
        drawString(fontRendererObj, "Approved infrastructure", left + 18, top + 94, 0xFFF0C674);
        label("Ramp block", left + 18, top + 118);
        label("Light block", left + 270, top + 118);
        label("Fluid filler", left + 18, top + 144);
        label("Light every", left + 270, top + 144);
        drawString(fontRendererObj, "Optional shared services", left + 18, top + 178, 0xFFF0C674);
        label("Loadout", left + 18, top + 208);
        label("Storage", left + 270, top + 208);
        label("Repair station", left + 18, top + 234);
        label("Tool slot", left + 270, top + 234);
        label("Work damage", left + 18, top + 260);
        drawString(fontRendererObj, "Center now: " + centerSummary(), left + 18, top + 286, 0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(status, 76),
            left + 18,
            top + 304,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void populateSavedNames() {
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) return;
        try {
            ProfileEnvelope profile = editor.get()
                .load();
            if (!profile.getNamedLoadouts()
                .isEmpty())
                loadoutId.setText(
                    profile.getNamedLoadouts()
                        .get(0)
                        .getId());
            if (!profile.getNamedStorageEndpoints()
                .isEmpty()) {
                storageId.setText(
                    profile.getNamedStorageEndpoints()
                        .get(0)
                        .getId());
            }
            if (!profile.getNamedRepairStations()
                .isEmpty()) {
                NamedRepairStation station = profile.getNamedRepairStations()
                    .get(0);
                stationId.setText(station.getId());
                loadoutId.setText(station.getLoadoutId());
            }
        } catch (RuntimeException failure) {
            status = "Nothing queued: " + safeMessage(failure);
        }
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(64);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { taskId, radius, bottomY, topY, rampMaterial, lightMaterial, fillerMaterial,
            lightInterval, loadoutId, storageId, stationId, toolSlot, workDamage };
    }

    private void label(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFE0E0E0);
    }

    private int currentY() {
        return mc != null && mc.thePlayer != null ? MathHelper.floor_double(mc.thePlayer.posY) : 64;
    }

    private String centerSummary() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.theWorld.provider == null)
            return "unavailable";
        return "dimension " + mc.theWorld.provider.dimensionId
            + ", X "
            + MathHelper.floor_double(mc.thePlayer.posX)
            + ", Z "
            + MathHelper.floor_double(mc.thePlayer.posZ);
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

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 3) + "...";
    }
}
