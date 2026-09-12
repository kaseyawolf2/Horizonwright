package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationTraversal;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSnapshot;
import io.github.kaseyawolf2.horizonwright.core.task.TaskSpec;
import io.github.kaseyawolf2.horizonwright.core.task.TaskState;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

/** Existing fallback work is paused before settings can be saved. */
public final class GuiExcavationSettings extends GuiReadableScreen {

    private final GuiScreen parent;
    private final CurrentRuntimeProvider provider;
    private TaskSpec original;
    private ExcavationTraversal order;
    private boolean walking;
    private GuiTextField bandWidth, storage, repair;
    private GuiButton save;
    private int left, top;
    private String message = "Pause the task, edit, then Save & resume.";

    public GuiExcavationSettings(GuiScreen parent, CurrentRuntimeProvider provider, TaskSpec spec) {
        this.parent = parent;
        this.provider = provider;
        this.original = spec;
        this.order = ExcavationTraversal.parse(
            spec.getParameters()
                .get("traversal"));
        this.walking = Boolean.parseBoolean(
            spec.getParameters()
                .get("movingMining"));
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        left = (width - 440) / 2;
        top = Math.max(8, (height - 282) / 2);
        bandWidth = field(
            170,
            72,
            58,
            original.getParameters()
                .getOrDefault("spiralWidth", "1"));
        storage = field(
            170,
            126,
            245,
            original.getParameters()
                .getOrDefault("service.storageId", ""));
        repair = field(
            170,
            154,
            245,
            original.getParameters()
                .getOrDefault("service.repairStationId", ""));
        buttonList.add(new GuiHorizonwrightButton(1, left + 18, top + 42, 397, 20, "Order: " + order.label()));
        buttonList.add(
            new GuiHorizonwrightButton(
                2,
                left + 18,
                top + 100,
                397,
                20,
                "Walk while mining: " + (walking ? "ON" : "off")));
        buttonList.add(new GuiHorizonwrightButton(3, left + 18, top + 248, 115, 20, "Pause to edit"));
        save = new GuiHorizonwrightButton(4, left + 141, top + 248, 175, 20, "Save & resume");
        buttonList.add(save);
        buttonList.add(new GuiHorizonwrightButton(5, left + 324, top + 248, 91, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 5) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 1) {
            order = ExcavationTraversal.values()[(order.ordinal() + 1) % ExcavationTraversal.values().length];
            button.displayString = "Order: " + order.label();
            return;
        }
        if (button.id == 2) {
            walking = !walking;
            button.displayString = "Walk while mining: " + (walking ? "ON" : "off");
            return;
        }
        try {
            io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime runtime = CurrentRuntimeUiResolver
                .resolve(provider)
                .getRuntime();
            if (button.id == 3) {
                runtime.pauseTask(original.getId());
                message = "Pause requested; waiting for the current action to finish safely.";
                return;
            }
            if (button.id == 4) {
                Map<String, String> params = new LinkedHashMap<>(original.getParameters());
                params.put("traversal", order.id());
                params.put(
                    "spiralWidth",
                    Integer.toString(
                        Integer.parseInt(
                            bandWidth.getText()
                                .trim())));
                params.put("movingMining", Boolean.toString(walking));
                if (storage.getText()
                    .trim()
                    .isEmpty()) {
                    params.remove("service.storageId");
                    params.remove("service.loadoutId");
                } else {
                    params.put("service.storageId", ProfileAssetInput.stableId(storage.getText(), "storage"));
                    params.putIfAbsent("service.loadoutId", AutomaticInventory.ID);
                }
                if (repair.getText()
                    .trim()
                    .isEmpty()) {
                    params.remove("service.repairStationId");
                    params.remove("service.reservedToolSlot");
                    params.remove("service.predictedWorkDamage");
                } else {
                    params
                        .put("service.repairStationId", ProfileAssetInput.stableId(repair.getText(), "repair station"));
                    params.putIfAbsent("service.reservedToolSlot", "0");
                    params.putIfAbsent("service.predictedWorkDamage", "1");
                }
                TaskSpec replacement = new TaskSpec(
                    original.getId(),
                    original.getType(),
                    original.getDisplayName(),
                    original.getLane(),
                    params);
                runtime.editExcavation(original, replacement);
                original = replacement;
                message = "Settings saved. Task remains paused if resume is unavailable.";
                runtime.resumeTask(original.getId());
                message = "Saved and queued to resume.";
            }
        } catch (RuntimeException failure) {
            message = failure.getMessage() == null ? "Unable to update task." : failure.getMessage();
        }
    }

    @Override
    protected void drawContents(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + 440, top + 282, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Fallback settings: " + original.getId(), width / 2, top + 15, 0xFFF0C674);
        drawString(fontRendererObj, "Spiral width (1-64)", left + 18, top + 78, 0xFFE0E0E0);
        drawString(fontRendererObj, "Storage (blank = off)", left + 18, top + 132, 0xFFE0E0E0);
        drawString(fontRendererObj, "Repair (blank = off)", left + 18, top + 160, 0xFFE0E0E0);
        drawParagraph(
            "Changing order/spiral width rescans the volume and resets scan statistics. Already cleared air is skipped. Bounds stay fixed.",
            left + 18,
            top + 184,
            397,
            34,
            0xFFB8C8DE);
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(provider);
        TaskSnapshot task = resolution.isAvailable() ? resolution.getRuntime()
            .controllerSnapshot()
            .findTask(original.getId())
            .orElse(null) : null;
        save.enabled = task != null && (task.getState() == TaskState.SUSPENDED || task.getState() == TaskState.BLOCKED);
        drawParagraph(message, left + 18, top + 222, 397, 24, 0xFF8FAAD0);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawContents(mouseX, mouseY, partialTicks);
    }

    private GuiTextField field(int x, int y, int w, String value) {
        GuiTextField field = readableField(fontRendererObj, left + x, top + y, w, 18);
        field.setMaxStringLength(80);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { bandWidth, storage, repair };
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields()) field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        for (GuiTextField field : fields()) field.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        for (GuiTextField field : fields()) field.mouseClicked(x, y, button);
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
