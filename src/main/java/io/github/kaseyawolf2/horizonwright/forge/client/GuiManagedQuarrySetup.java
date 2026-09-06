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
public final class GuiManagedQuarrySetup extends GuiReadableScreen {

    private static final int BACK_BUTTON = 1;
    private static final int SUBMIT_BUTTON = 2;
    private static final int SERVICES_BUTTON = 3;

    private io.github.kaseyawolf2.horizonwright.core.base.NamedArea boundArea;
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

    public GuiManagedQuarrySetup(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider editorProvider, io.github.kaseyawolf2.horizonwright.core.base.NamedArea area) {
        this(parent, runtimeProvider, editorProvider);
        this.boundArea = area;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(500, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - 384) / 2);
        taskId = field(left + 138, top + 44, 120, "quarry-1");
        radius = field(left + 356, top + 44, 48, "8");
        bottomY = field(left + 138, top + 70, 48, Integer.toString(currentY() - 8));
        topY = field(left + 356, top + 70, 48, Integer.toString(currentY()));
        rampMaterial = field(left + 138, top + (boundArea == null ? 112 : 68), 120, "minecraft:cobblestone");
        lightMaterial = field(left + 356, top + (boundArea == null ? 112 : 68), 120, "minecraft:torch");
        fillerMaterial = field(left + 138, top + (boundArea == null ? 138 : 94), 120, "minecraft:cobblestone");
        lightInterval = field(left + 356, top + (boundArea == null ? 138 : 94), 48, "4");
        loadoutId = field(left + 138, top + (boundArea == null ? 202 : 158), 120, "mining");
        storageId = field(left + 356, top + (boundArea == null ? 202 : 158), 120, "ore-chest");
        stationId = field(left + 138, top + (boundArea == null ? 228 : 184), 120, "tool-forge");
        toolSlot = field(left + 356, top + (boundArea == null ? 228 : 184), 48, "0");
        workDamage = field(left + 138, top + (boundArea == null ? 254 : 210), 48, "1");
        servicesButton = new GuiHorizonwrightButton(
            SERVICES_BUTTON,
            left + 282,
            top + (boundArea == null ? 254 : 210),
            194,
            20,
            "Services: ON");
        buttonList.add(servicesButton);
        buttonList.add(
            new GuiHorizonwrightButton(
                SUBMIT_BUTTON,
                left + 18,
                top + (boundArea == null ? 326 : 282),
                panelWidth - 36,
                22,
                "Queue managed quarry"));
        buttonList.add(
            new GuiHorizonwrightButton(
                BACK_BUTTON,
                left + panelWidth - 82,
                top + (boundArea == null ? 354 : 310),
                70,
                20,
                "Back"));
        populateSavedNames();
        loadoutId.setText(AutomaticInventory.ID);
        loadoutId.setVisible(false);
        toolSlot.setVisible(false);
        storageId.setText(boundArea == null ? "default-chest" : boundArea.resolvedStorageId());
        stationId.setText("default-repair");
        if (boundArea != null) {
            taskId.setText(boundArea.getId());
            for (GuiTextField inherited : new GuiTextField[] { taskId, radius, bottomY, topY }) {
                inherited.setVisible(false);
                inherited.setEnabled(false);
            }
            int span = Math.min(
                boundArea.getMaximum()
                    .getX()
                    - boundArea.getMinimum()
                        .getX(),
                boundArea.getMaximum()
                    .getZ()
                    - boundArea.getMinimum()
                        .getZ());
            radius.setText(Integer.toString(boundArea.isCircular() ? span / 2 : Math.max(2, span / 2)));
            bottomY.setText(
                Integer.toString(
                    boundArea.getMinimum()
                        .getY()));
            topY.setText(
                Integer.toString(
                    boundArea.getMaximum()
                        .getY()));
            radius.setEnabled(false);
            bottomY.setEnabled(false);
            topY.setEnabled(false);
            storageId.setEnabled(false);
            status = "Uses the saved " + (boundArea.isCircular() ? "circle" : "rectangle")
                + " exactly; edit bounds from Areas.";
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
            int centerX = boundArea == null ? MathHelper.floor_double(mc.thePlayer.posX)
                : boundArea.getMinimum()
                    .getX()
                    + (boundArea.getMaximum()
                        .getX()
                        - boundArea.getMinimum()
                            .getX())
                        / 2;
            int centerZ = boundArea == null ? MathHelper.floor_double(mc.thePlayer.posZ)
                : boundArea.getMinimum()
                    .getZ()
                    + (boundArea.getMaximum()
                        .getZ()
                        - boundArea.getMinimum()
                            .getZ())
                        / 2;
            String id = boundArea == null ? ProfileAssetInput.stableId(taskId.getText(), "task name")
                : boundArea.getId() + "-mine-" + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld);
            if (boundArea != null && boundArea.getMinimum()
                .getDimensionId() != mc.theWorld.provider.dimensionId)
                throw new IllegalArgumentException("Travel to the area's dimension first.");
            int parsedRadius = boundArea == null ? ProfileAssetInput.nonNegativeInteger(radius.getText(), "radius")
                : boundArea.isCircular() ? boundArea.getRadius() : 2;
            int parsedBottom = boundArea == null ? integer(bottomY.getText(), "bottom Y")
                : boundArea.getMinimum()
                    .getY();
            int parsedTop = boundArea == null ? integer(topY.getText(), "top Y")
                : boundArea.getMaximum()
                    .getY();
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
                editor.apply(
                    io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate
                        .of(null, AutomaticInventory.inspect(mc, editor.load()), null, null));
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
            if (boundArea != null)
                spec = io.github.kaseyawolf2.horizonwright.runtime.task.ExcavationTask.forArea(spec, boundArea);
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
    protected void drawContents(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + (boundArea == null ? 384 : 340), 0xE010141B);
        drawCenteredString(fontRendererObj, "New managed quarry", width / 2, top + 10, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Retained ramp, approved lights, and fluid filler",
            width / 2,
            top + 25,
            0xFF8FAAD0);
        if (boundArea == null) {
            label("Task name", left + 18, top + 50);
            label(
                boundArea != null && !boundArea.isCircular() ? "Saved rectangle" : "Radius (2-250)",
                left + 270,
                top + 50);
            label("Bottom Y", left + 18, top + 76);
            label("Top Y", left + 270, top + 76);
        } else {
            drawString(fontRendererObj, "Area: " + boundArea.getDisplayName(), left + 18, top + 50, 0xFFB8C8DE);
        }
        drawString(
            fontRendererObj,
            "Approved infrastructure",
            left + 18,
            top + (boundArea == null ? 94 : 50),
            0xFFF0C674);
        label("Ramp block", left + 18, top + (boundArea == null ? 118 : 74));
        label("Light block", left + 270, top + (boundArea == null ? 118 : 74));
        label("Fluid filler", left + 18, top + (boundArea == null ? 144 : 100));
        label("Light every", left + 270, top + (boundArea == null ? 144 : 100));
        drawString(
            fontRendererObj,
            "Optional shared services",
            left + 18,
            top + (boundArea == null ? 178 : 134),
            0xFFF0C674);
        drawString(fontRendererObj, "Tools: automatic", left + 18, top + (boundArea == null ? 208 : 164), 0xFFB8C8DE);
        label("Storage", left + 270, top + (boundArea == null ? 208 : 164));
        label("Repair station", left + 18, top + (boundArea == null ? 234 : 190));

        label("Work damage", left + 18, top + (boundArea == null ? 260 : 216));
        drawString(
            fontRendererObj,
            "Center now: " + centerSummary(),
            left + 18,
            top + (boundArea == null ? 286 : 242),
            0xFFB8C8DE);
        drawString(
            fontRendererObj,
            truncate(status, 76),
            left + 18,
            top + (boundArea == null ? 304 : 260),
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawContents(mouseX, mouseY, partialTicks);
        if (mouseX >= left + 18 && mouseX < left + 190
            && mouseY >= top + (boundArea == null ? 254 : 210)
            && mouseY < top + (boundArea == null ? 274 : 230))
            drawHoveringText(
                java.util.Arrays.asList(
                    "Work damage (legacy estimate)",
                    "Estimated tool durability points used by upcoming work.",
                    "Not block damage, mining speed, or a percentage.",
                    "Currently recorded for diagnostics only; it does not trigger repairs.",
                    "The default Tinkers policy repairs tools when broken.",
                    "Leave at 1 unless testing diagnostics."),
                mouseX,
                mouseY,
                fontRendererObj);
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
        GuiTextField field = readableField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(64);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { taskId, radius, bottomY, topY, rampMaterial, lightMaterial, fillerMaterial,
            lightInterval, storageId, stationId, workDamage };
    }

    private void label(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFE0E0E0);
    }

    private int currentY() {
        return mc != null && mc.thePlayer != null ? MathHelper.floor_double(mc.thePlayer.posY) : 64;
    }

    private String centerSummary() {
        if (boundArea != null) return "saved area " + boundArea.getDisplayName()
            + " (dimension "
            + boundArea.getMinimum()
                .getDimensionId()
            + ")";
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
        return value;
    }
}
