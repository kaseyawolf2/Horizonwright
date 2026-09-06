package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.base.AnimalObservation;
import io.github.kaseyawolf2.horizonwright.core.base.HusbandryObservation;
import io.github.kaseyawolf2.horizonwright.core.base.LivestockSpecies;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.ScheduleState;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.forge.client.husbandry.MinecraftHusbandryObserver;
import io.github.kaseyawolf2.horizonwright.forge.client.husbandry.ProfileHusbandryConfiguration;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.HusbandryTask;

/** Type-safe husbandry task and recurring-schedule editor for one saved pen. */
public final class GuiHusbandrySetup extends GuiReadableScreen {

    private static final int BACK_BUTTON = 1;
    private static final int CLOSE_BUTTON = 2;
    private static final int SPECIES_BUTTON = 3;
    private static final int QUEUE_BUTTON = 4;
    private static final int SCHEDULE_BUTTON = 5;
    private static final int SCAN_BUTTON = 6;
    private static final int CULL_BUTTON = 7;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editorProvider;
    private final NamedArea pen;
    private LivestockSpecies species = LivestockSpecies.COW;
    private GuiTextField minimumAdults;
    private GuiTextField desiredHerdSize;
    private GuiTextField intervalMinutes;
    private GuiButton speciesButton;
    private GuiButton cullButton;
    private boolean allowCulling;
    private int left;
    private int top;
    private int panelWidth;
    private String status = "Breed eligible pairs, wait for babies, then replace adults. Keep the pen closed.";

    public GuiHusbandrySetup(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider editorProvider, NamedArea pen) {
        if (parent == null || runtimeProvider == null || editorProvider == null || pen == null) {
            throw new IllegalArgumentException("parent, runtime/profile providers, and named pen are required");
        }
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
        this.editorProvider = editorProvider;
        this.pen = pen;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        panelWidth = Math.min(460, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 300) / 2);
        speciesButton = new GuiHorizonwrightButton(SPECIES_BUTTON, left + 148, top + 54, 180, 20, "");
        buttonList.add(speciesButton);
        buttonList.add(new GuiHorizonwrightButton(SCAN_BUTTON, left + 336, top + 54, 106, 20, "Scan loaded pen"));
        minimumAdults = field(left + 148, top + 92, 72, "2");
        desiredHerdSize = field(left + 148, top + 122, 72, "10");
        intervalMinutes = field(left + 148, top + 182, 72, "30");
        cullButton = new GuiHorizonwrightButton(CULL_BUTTON, left + 230, top + 122, 212, 20, "");
        buttonList.add(cullButton);
        buttonList.add(new GuiHorizonwrightButton(QUEUE_BUTTON, left + 18, top + 218, 196, 22, "Queue one pass"));
        buttonList.add(
            new GuiHorizonwrightButton(
                SCHEDULE_BUTTON,
                left + 222,
                top + 218,
                panelWidth - 240,
                22,
                "Schedule passes"));
        buttonList.add(new GuiHorizonwrightButton(CLOSE_BUTTON, left + 18, top + 264, 64, 20, "Close"));
        buttonList.add(new GuiHorizonwrightButton(BACK_BUTTON, left + panelWidth - 82, top + 264, 64, 20, "Back"));
        loadDefaults();
        updateSpeciesButton();
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
        if (button.id == SPECIES_BUTTON) {
            LivestockSpecies[] values = LivestockSpecies.values();
            species = values[(species.ordinal() + 1) % values.length];
            loadDefaults();
            updateSpeciesButton();
            return;
        }
        if (button.id == CULL_BUTTON) {
            allowCulling = !allowCulling;
            updateSpeciesButton();
            status = allowCulling ? "Culling authorized for this pass/schedule; protected animals are excluded."
                : "Culling disabled; feeding and collection can still run.";
            return;
        }
        try {
            if (button.id == SCAN_BUTTON) scanPen();
            else if (button.id == QUEUE_BUTTON) queuePass();
            else if (button.id == SCHEDULE_BUTTON) schedulePasses();
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void scanPen() {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            throw new IllegalStateException("join the bound world first");
        }
        HusbandryObservation observation = new MinecraftHusbandryObserver(
            mc,
            new ProfileHusbandryConfiguration(editorProvider)).observe(pen.getId());
        int total = 0;
        int adults = 0;
        int ready = 0;
        int engaged = 0;
        int protectedStock = 0;
        int excluded = 0;
        for (AnimalObservation animal : observation.getAnimals()) {
            if (animal.getSpecies() != species) continue;
            total++;
            if (animal.isAdult()) adults++;
            if (animal.isReadyToBreed()) ready++;
            if (animal.isBreedingEngaged()) engaged++;
            if (animal.isProtectedStock()) protectedStock++;
            if (!animal.isEligibleTarget()) excluded++;
        }
        status = "Scan " + species.name()
            + ": total "
            + total
            + ", adults "
            + adults
            + ", ready "
            + ready
            + ", breeding "
            + engaged
            + ", protected "
            + protectedStock
            + ", excluded "
            + excluded
            + "; drops "
            + observation.getDrops()
                .size()
            + ".";
    }

    private void queuePass() {
        HorizonwrightRuntime runtime = runtime();
        Values values = values();
        String taskId = "husbandry-" + pen.getId()
            + "-"
            + species.name()
                .toLowerCase()
            + "-"
            + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld);
        TaskSnapshot task = runtime.submitHusbandry(
            HusbandryTask.finitePass(
                taskId,
                pen.getId(),
                species,
                values.minimumAdults,
                values.maximumAdults,
                values.maximumActions,
                allowCulling));
        status = "Queued '" + task.getSpec()
            .getId() + "'; culling " + (allowCulling ? "enabled." : "disabled.");
    }

    private void schedulePasses() {
        HorizonwrightRuntime runtime = runtime();
        Values values = values();
        long intervalMillis = Math.multiplyExact((long) values.intervalMinutes, 60_000L);
        String scheduleId = scheduleId();
        ScheduleSnapshot existing = findSchedule(runtime, scheduleId);
        if (existing == null) {
            runtime.scheduleHusbandry(
                scheduleId,
                pen.getId(),
                species,
                values.minimumAdults,
                values.maximumAdults,
                values.maximumActions,
                allowCulling,
                intervalMillis);
            status = "Scheduled '" + scheduleId + "'; culling " + (allowCulling ? "enabled." : "disabled.");
        } else {
            runtime.updateHusbandrySchedule(
                scheduleId,
                pen.getId(),
                species,
                values.minimumAdults,
                values.maximumAdults,
                values.maximumActions,
                allowCulling,
                intervalMillis);
            if (existing.getState() == ScheduleState.PAUSED) runtime.resumeSchedule(scheduleId);
            status = "Updated recurring livestock policy '" + scheduleId + "'.";
        }
    }

    private void loadDefaults() {
        allowCulling = false;
        minimumAdults.setText("2");
        desiredHerdSize.setText("10");
        intervalMinutes.setText("30");
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) return;
        ScheduleSnapshot existing = findSchedule(resolution.getRuntime(), scheduleId());
        if (existing == null || !HusbandryTask.TYPE.equals(
            existing.getRule()
                .getTask()
                .getType()))
            return;
        allowCulling = HusbandryTask.allowCulling(
            existing.getRule()
                .getTask());
        desiredHerdSize.setText(
            Integer.toString(
                HusbandryTask.maximumAdults(
                    existing.getRule()
                        .getTask())));
        minimumAdults.setText(
            Integer.toString(
                HusbandryTask.minimumAdults(
                    existing.getRule()
                        .getTask())));
        long interval = existing.getRule()
            .getIntervalMillis();
        if (interval >= 60_000L && interval % 60_000L == 0L) {
            intervalMinutes.setText(Long.toString(interval / 60_000L));
        }
    }

    private Values values() {
        int minimum = ProfileAssetInput.positiveInteger(minimumAdults.getText(), "minimum adults");
        int minutes = ProfileAssetInput.positiveInteger(intervalMinutes.getText(), "interval minutes");
        if (minimum < 2) throw new IllegalArgumentException("minimum adults must preserve at least one breeding pair");
        int desired = ProfileAssetInput.positiveInteger(desiredHerdSize.getText(), "desired herd size");
        if (desired < minimum || desired > 1000)
            throw new IllegalArgumentException("desired herd size must be at least minimum adults and at most 1000");
        return new Values(minimum, desired, 16, minutes);
    }

    private HorizonwrightRuntime runtime() {
        if (mc == null || mc.theWorld == null) throw new IllegalStateException("join the bound world first");
        return CurrentRuntimeUiResolver.resolve(runtimeProvider)
            .getRuntime();
    }

    private String scheduleId() {
        return "husbandry-" + pen.getId()
            + "-"
            + species.name()
                .toLowerCase();
    }

    private static ScheduleSnapshot findSchedule(HorizonwrightRuntime runtime, String scheduleId) {
        for (ScheduleSnapshot schedule : runtime.controllerSnapshot()
            .getScheduler()
            .getSchedules()) {
            if (scheduleId.equals(
                schedule.getRule()
                    .getId()))
                return schedule;
        }
        return null;
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
        drawRect(left, top, left + panelWidth, top + 292, 0xEE10141B);
        drawCenteredString(
            fontRendererObj,
            "Livestock policy: " + pen.getDisplayName(),
            width / 2,
            top + 14,
            0xFFF0C674);
        drawCenteredString(fontRendererObj, "Named pen: " + pen.getId(), width / 2, top + 29, 0xFF8FAAD0);
        label("Species", top + 60);
        label("Minimum adults", top + 98);
        label("Desired herd size", top + 128);
        label("Herd = adults + babies; breed before culling", top + 158);
        label("Every minutes", top + 188);
        drawParagraph(
            status,
            left + 18,
            top + 244,
            panelWidth - 36,
            20,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawContents(mouseX, mouseY, partialTicks);
    }

    private void label(String value, int y) {
        drawString(fontRendererObj, value, left + 18, y, 0xFFE0E0E0);
    }

    private void updateSpeciesButton() {
        speciesButton.displayString = species.name();
        cullButton.displayString = "Allow animal attacks: " + (allowCulling ? "ON" : "OFF");
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = readableField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(8);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { minimumAdults, desiredHerdSize, intervalMinutes };
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class Values {

        private final int minimumAdults;
        private final int maximumAdults;
        private final int maximumActions;
        private final int intervalMinutes;

        private Values(int minimumAdults, int maximumAdults, int maximumActions, int intervalMinutes) {
            this.minimumAdults = minimumAdults;
            this.maximumAdults = maximumAdults;
            this.maximumActions = maximumActions;
            this.intervalMinutes = intervalMinutes;
        }
    }
}
