package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask;

/** Shared destinations and facilities, independent of individual areas. */
public final class GuiBaseSettings extends GuiReadableScreen {

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editors;
    private String message = "Look at a chest, station, or bed before opening this page to register it.";
    private int left, top;

    public GuiBaseSettings(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
        ProfileAssetEditorProvider editors) {
        this.parent = parent;
        this.runtimeProvider = runtimeProvider;
        this.editors = editors;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        left = (width - 500) / 2;
        top = (height - 350) / 2;
        add(1, "Set default chest from targeted block", 62);
        add(2, "Register targeted repair station", 124);
        add(4, "Register targeted bed", 208);
        buttonList.add(new GuiHorizonwrightButton(5, left + 18, top + 236, 220, 20, "Sleep once"));
        buttonList.add(new GuiHorizonwrightButton(6, left + 246, top + 236, 236, 20, "Every night"));
        buttonList.add(new GuiHorizonwrightButton(0, left + 402, top + 316, 80, 20, "Back"));
    }

    private void add(int id, String text, int y) {
        buttonList.add(new GuiHorizonwrightButton(id, left + 18, top + y, 464, 20, text));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
            return;
        }
        try {
            ProfileAssetEditor editor = editors.getCurrentProfileAssetEditor()
                .orElseThrow(() -> new IllegalStateException("Join the bound world first."));
            if (button.id == 1) {
                BaseAssetCapture.chest(mc, editor, "default-chest");
                message = "Default chest saved. Areas without an override use this chest.";
            }
            if (button.id == 2) {
                NamedLocation location = BaseAssetCapture.location(mc, "default-repair-location");
                Object tile = MinecraftRuntimeAccess
                    .tileEntity(mc.theWorld, location.getX(), location.getY(), location.getZ());
                if (tile == null || !tile.getClass()
                    .getName()
                    .startsWith("tconstruct.")) throw new IllegalArgumentException("Look at a Tinkers repair station.");
                editor.apply(
                    ProfileAssetUpdate.of(
                        location,
                        AutomaticInventory.inspect(mc, editor.load()),
                        null,
                        new NamedRepairStation(
                            "default-repair",
                            "Repair station",
                            location.getId(),
                            AutomaticInventory.ID)));
                message = "Repair station saved. Tools are discovered from inventory.";
            }
            if (button.id == 3) {
                ItemStack held = mc.thePlayer.getHeldItem();
                ItemFingerprint item = new MinecraftContainerSnapshotter().fingerprint(held);
                if (item == null) throw new IllegalArgumentException("Hold the repair material first.");
                if (held.getMaxStackSize() == 1 || !held.getItem()
                    .getToolClasses(held)
                    .isEmpty()) throw new IllegalArgumentException("Hold a repair material, not a tool.");
                List<LoadoutReservation> items = new ArrayList<>();
                for (LoadoutReservation value : AutomaticInventory.inspect(mc, editor.load())
                    .getReservations()) if (value.getRole() != LoadoutRole.REPAIR_MATERIAL) items.add(value);
                items.add(
                    new LoadoutReservation(
                        "repair-supply",
                        LoadoutRole.REPAIR_MATERIAL,
                        item.getItemId(),
                        item.getMetadata(),
                        null,
                        Math.min(16, item.getCount())));
                editor.apply(
                    ProfileAssetUpdate
                        .of(null, new NamedLoadout(AutomaticInventory.ID, "Automatic inventory", items), null, null));
                message = "Repair supply saved: " + held.getDisplayName();
            }
            if (button.id == 4) {
                NamedLocation location = BaseAssetCapture.location(mc, "home-bed");
                if (MinecraftRuntimeAccess.block(mc.theWorld, location.getX(), location.getY(), location.getZ())
                    != Blocks.bed) throw new IllegalArgumentException("Look at a vanilla bed first.");
                editor.apply(ProfileAssetUpdate.of(location, null, null, null));
                message = "Home bed saved.";
            }
            if (button.id == 5) {
                CurrentRuntimeUiResolver.resolve(runtimeProvider)
                    .getRuntime()
                    .submitSleep(
                        SleepTask.once("sleep-" + MinecraftRuntimeAccess.totalWorldTime(mc.theWorld), "home-bed"));
                message = "Sleep task queued.";
            }
            if (button.id == 6) {
                CurrentRuntimeUiResolver.resolve(runtimeProvider)
                    .getRuntime()
                    .scheduleNightSleep("sleep-home-bed", "home-bed");
                message = "Nightly sleep scheduled.";
            }
        } catch (RuntimeException failure) {
            message = "Nothing changed: " + failure.getMessage();
        }
    }

    @Override
    protected void drawContents(int mx, int my, float ticks) {
        drawDefaultBackground();
        drawRect(left, top, left + 500, top + 350, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Base", width / 2, top + 14, 0xFFF0C674);
        drawString(fontRendererObj, "Storage: default destination for every area", left + 18, top + 42, 0xFFB8C8DE);
        drawString(
            fontRendererObj,
            "Tools: automatic inventory inspection; no manual loadouts",
            left + 18,
            top + 94,
            0xFFB8C8DE);
        drawString(fontRendererObj, "Sleeping", left + 18, top + 188, 0xFFB8C8DE);
        drawParagraph(message, left + 18, top + 270, 464, 36, 0xFFB8C8DE);
        super.drawContents(mx, my, ticks);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
