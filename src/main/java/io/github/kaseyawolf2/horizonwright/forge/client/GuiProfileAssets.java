package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutReservation;
import io.github.kaseyawolf2.horizonwright.core.logistics.LoadoutRole;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.logistics.StorageItemFilter;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.forge.client.container.MinecraftContainerSnapshotter;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;
import io.github.kaseyawolf2.horizonwright.runtime.task.SleepTask;

/** Guided named-asset editor which captures inventory and world evidence instead of requiring JSON. */
public final class GuiProfileAssets extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int SAVE_LOADOUT_BUTTON = 2;
    private static final int SAVE_CHEST_BUTTON = 3;
    private static final int SAVE_STATION_BUTTON = 4;
    private static final int NEW_EXCAVATION_BUTTON = 5;
    private static final int WORK_AREAS_BUTTON = 6;
    private static final int SAVE_BED_BUTTON = 7;
    private static final int QUEUE_SLEEP_BUTTON = 8;
    private static final int SCHEDULE_SLEEP_BUTTON = 9;

    private final GuiScreen parent;
    private final CurrentRuntimeProvider runtimeProvider;
    private final ProfileAssetEditorProvider editorProvider;
    private final MinecraftContainerSnapshotter snapshots = new MinecraftContainerSnapshotter();

    private GuiTextField loadoutId;
    private GuiTextField toolSlot;
    private GuiTextField materialSlot;
    private GuiTextField materialMinimum;
    private GuiTextField storageId;
    private GuiTextField stationId;
    private GuiTextField bedId;
    private String status = "Choose inventory slots, or look at a block and save it.";
    private int left;
    private int top;
    private int panelWidth;

    public GuiProfileAssets(GuiScreen parent, CurrentRuntimeProvider runtimeProvider,
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
        top = Math.max(6, (height - 342) / 2);
        loadoutId = field(left + 132, top + 50, 126, "mining");
        toolSlot = field(left + 334, top + 50, 38, "0");
        materialSlot = field(left + 132, top + 76, 38, "1");
        materialMinimum = field(left + 334, top + 76, 38, "16");
        storageId = field(left + 132, top + 132, 126, "ore-chest");
        stationId = field(left + 132, top + 188, 126, "tool-forge");
        bedId = field(left + 132, top + 238, 126, "home-bed");
        buttonList.add(new GuiButton(SAVE_LOADOUT_BUTTON, left + 382, top + 50, 96, 20, "Save loadout"));
        buttonList.add(new GuiButton(SAVE_CHEST_BUTTON, left + 282, top + 132, 196, 20, "Save targeted vanilla chest"));
        buttonList
            .add(new GuiButton(SAVE_STATION_BUTTON, left + 282, top + 188, 196, 20, "Save targeted repair station"));
        buttonList.add(new GuiButton(SAVE_BED_BUTTON, left + 282, top + 238, 196, 20, "Save targeted vanilla bed"));
        buttonList.add(new GuiButton(QUEUE_SLEEP_BUTTON, left + 282, top + 262, 94, 20, "Sleep once"));
        buttonList.add(new GuiButton(SCHEDULE_SLEEP_BUTTON, left + 382, top + 262, 96, 20, "Every night"));
        buttonList.add(new GuiButton(BACK_BUTTON, left + panelWidth - 82, top + 310, 70, 20, "Back"));
        buttonList.add(new GuiButton(NEW_EXCAVATION_BUTTON, left + 12, top + 310, 128, 20, "New excavation"));
        buttonList.add(new GuiButton(WORK_AREAS_BUTTON, left + 146, top + 310, 110, 20, "Work areas"));
        refreshStatus();
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
        if (button.id == NEW_EXCAVATION_BUTTON) {
            mc.displayGuiScreen(new GuiExcavationSetup(this, runtimeProvider, editorProvider));
            return;
        }
        if (button.id == WORK_AREAS_BUTTON) {
            mc.displayGuiScreen(new GuiProfileAreas(this, runtimeProvider, editorProvider));
            return;
        }
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) {
            status = "Profile editor unavailable. Join the bound world and reopen this page.";
            return;
        }
        try {
            if (button.id == SAVE_LOADOUT_BUTTON) saveLoadout(editor.get());
            else if (button.id == SAVE_CHEST_BUTTON) saveChest(editor.get());
            else if (button.id == SAVE_STATION_BUTTON) saveStation(editor.get());
            else if (button.id == SAVE_BED_BUTTON) saveBed(editor.get());
            else if (button.id == QUEUE_SLEEP_BUTTON) queueSleep();
            else if (button.id == SCHEDULE_SLEEP_BUTTON) scheduleSleep();
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private void saveLoadout(ProfileAssetEditor editor) {
        requirePlayer();
        String id = ProfileAssetInput.stableId(loadoutId.getText(), "loadout name");
        int toolIndex = ProfileAssetInput.inventorySlot(toolSlot.getText(), "tool slot");
        int materialIndex = ProfileAssetInput.inventorySlot(materialSlot.getText(), "repair material slot");
        int minimum = ProfileAssetInput.positiveInteger(materialMinimum.getText(), "repair material minimum");
        ItemFingerprint tool = requiredFingerprint(mc.thePlayer.inventory.getStackInSlot(toolIndex), "tool slot");
        ItemFingerprint material = requiredFingerprint(
            mc.thePlayer.inventory.getStackInSlot(materialIndex),
            "repair material slot");
        if (minimum > material.getCount()) {
            throw new IllegalArgumentException(
                "repair material minimum exceeds the " + material.getCount() + " item(s) currently in that slot");
        }
        NamedLoadout loadout = new NamedLoadout(
            id,
            displayName(id),
            Arrays.asList(
                reservation("tool", LoadoutRole.TOOL, tool, 1),
                reservation("repair-material", LoadoutRole.REPAIR_MATERIAL, material, minimum)));
        editor.apply(ProfileAssetUpdate.of(null, loadout, null, null));
        status = "Saved loadout '" + id + "'. It reserves only the selected tool and repair material.";
    }

    private void saveChest(ProfileAssetEditor editor) {
        Target target = target();
        if (!(target.tile instanceof TileEntityChest) || target.tile.getClass() != TileEntityChest.class) {
            throw new IllegalArgumentException("look directly at a vanilla chest block first");
        }
        String id = ProfileAssetInput.stableId(storageId.getText(), "storage name");
        String locationId = id + "-location";
        NamedLocation location = target.location(locationId, displayName(id) + " location");
        NamedStorageEndpoint endpoint = new NamedStorageEndpoint(
            id,
            displayName(id),
            locationId,
            StorageItemFilter.acceptAll());
        editor.apply(ProfileAssetUpdate.of(location, null, endpoint, null));
        status = "Saved vanilla chest '" + id + "' at " + target.coordinates() + ". Filter: accept all.";
    }

    private void saveStation(ProfileAssetEditor editor) {
        Target target = target();
        if (target.tile == null) throw new IllegalArgumentException("look directly at the repair station block first");
        String id = ProfileAssetInput.stableId(stationId.getText(), "repair station name");
        String configuredLoadout = ProfileAssetInput.stableId(loadoutId.getText(), "loadout name");
        ProfileEnvelope profile = editor.load();
        boolean found = false;
        for (NamedLoadout candidate : profile.getNamedLoadouts()) {
            if (candidate.getId()
                .equals(configuredLoadout)) found = true;
        }
        if (!found) throw new IllegalArgumentException("save loadout '" + configuredLoadout + "' first");
        String locationId = id + "-location";
        NamedLocation location = target.location(locationId, displayName(id) + " location");
        NamedRepairStation station = new NamedRepairStation(id, displayName(id), locationId, configuredLoadout);
        editor.apply(ProfileAssetUpdate.of(location, null, null, station));
        status = "Saved repair station '" + id + "' at " + target.coordinates() + ".";
    }

    private void saveBed(ProfileAssetEditor editor) {
        Target target = target();
        if (mc.theWorld.getBlock(target.x, target.y, target.z) != Blocks.bed) {
            throw new IllegalArgumentException("look directly at a vanilla bed block first");
        }
        String id = ProfileAssetInput.stableId(bedId.getText(), "bed name");
        editor.apply(ProfileAssetUpdate.of(target.location(id, displayName(id)), null, null, null));
        status = "Saved registered bed '" + id + "' at " + target.coordinates() + ".";
    }

    private void queueSleep() {
        String id = ProfileAssetInput.stableId(bedId.getText(), "bed name");
        requireSavedLocation(id);
        io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime runtime = requireRuntime();
        long suffix = mc.theWorld == null ? 0L : Math.max(0L, mc.theWorld.getTotalWorldTime());
        runtime.submitSleep(SleepTask.once("sleep-" + id + "-" + suffix, id));
        status = "Queued one safe sleep attempt at '" + id + "'.";
    }

    private void scheduleSleep() {
        String id = ProfileAssetInput.stableId(bedId.getText(), "bed name");
        requireSavedLocation(id);
        requireRuntime().scheduleNightSleep("sleep-" + id, id);
        status = "Scheduled one safe attempt per vanilla night at '" + id + "'.";
    }

    private io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime requireRuntime() {
        CurrentRuntimeUiResolver.Resolution resolution = CurrentRuntimeUiResolver.resolve(runtimeProvider);
        if (!resolution.isAvailable()) throw new IllegalStateException(resolution.getDiagnostic());
        return resolution.getRuntime();
    }

    private void requireSavedLocation(String id) {
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) throw new IllegalStateException("active profile assets are unavailable");
        for (NamedLocation location : editor.get()
            .load()
            .getNamedLocations()) {
            if (location.getId()
                .equals(id)) return;
        }
        throw new IllegalStateException("save registered bed '" + id + "' first");
    }

    @Override
    public void updateScreen() {
        loadoutId.updateCursorCounter();
        toolSlot.updateCursorCounter();
        materialSlot.updateCursorCounter();
        materialMinimum.updateCursorCounter();
        storageId.updateCursorCounter();
        stationId.updateCursorCounter();
        bedId.updateCursorCounter();
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
        drawRect(left, top, left + panelWidth, top + 342, 0xE010141B);
        drawCenteredString(fontRendererObj, "Horizonwright profile assets", width / 2, top + 14, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            "Guided capture - no registry names, NBT, coordinates, or JSON",
            width / 2,
            top + 29,
            0xFF8FAAD0);
        label("Loadout name", left + 18, top + 56);
        label("Tool slot", left + 270, top + 56);
        label("Material slot", left + 18, top + 82);
        label("Keep at least", left + 220, top + 82);
        drawString(fontRendererObj, "Tool: " + selectedStackName(toolSlot), left + 18, top + 106, 0xFFB8C8DE);
        drawString(
            fontRendererObj,
            "Repair material: " + selectedStackName(materialSlot),
            left + 18,
            top + 118,
            0xFFB8C8DE);
        label("Chest name", left + 18, top + 138);
        drawString(
            fontRendererObj,
            "2. Close this page, look at the vanilla chest, reopen and save.",
            left + 18,
            top + 158,
            0xFFB8C8DE);
        label("Station name", left + 18, top + 194);
        drawString(
            fontRendererObj,
            "3. Look at the Tinkers station block and save it.",
            left + 18,
            top + 214,
            0xFFB8C8DE);
        label("Bed name", left + 18, top + 244);
        drawString(
            fontRendererObj,
            truncate(status, 76),
            left + 18,
            top + 288,
            status.startsWith("Nothing") ? 0xFFFF7777 : 0xFFB8C8DE);
        for (GuiTextField field : fields()) field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshStatus() {
        Optional<ProfileAssetEditor> editor = editorProvider.getCurrentProfileAssetEditor();
        if (!editor.isPresent()) {
            status = "Profile editor unavailable. Join the bound world and reopen this page.";
            return;
        }
        try {
            ProfileEnvelope profile = editor.get()
                .load();
            status = "Saved: " + profile.getNamedLoadouts()
                .size()
                + " loadout(s), "
                + profile.getNamedStorageEndpoints()
                    .size()
                + " chest(s), "
                + profile.getNamedRepairStations()
                    .size()
                + " repair station(s).";
        } catch (RuntimeException failure) {
            status = "Nothing changed: " + safeMessage(failure);
        }
    }

    private GuiTextField field(int x, int y, int width, String value) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(48);
        field.setText(value);
        return field;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] { loadoutId, toolSlot, materialSlot, materialMinimum, storageId, stationId, bedId };
    }

    private void label(String text, int x, int y) {
        drawString(fontRendererObj, text, x, y, 0xFFE0E0E0);
    }

    private Target target() {
        requirePlayer();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            throw new IllegalArgumentException("look directly at the block first");
        }
        TileEntity tile = mc.theWorld.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
        return new Target(mc.theWorld.provider.dimensionId, hit.blockX, hit.blockY, hit.blockZ, tile);
    }

    private void requirePlayer() {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.theWorld.provider == null) {
            throw new IllegalStateException("join the bound world first");
        }
    }

    private ItemFingerprint requiredFingerprint(ItemStack stack, String field) {
        ItemFingerprint fingerprint = snapshots.fingerprint(stack);
        if (fingerprint == null) throw new IllegalArgumentException(field + " is empty");
        return fingerprint;
    }

    private static LoadoutReservation reservation(String id, LoadoutRole role, ItemFingerprint item, int minimum) {
        return new LoadoutReservation(id, role, item.getItemId(), item.getMetadata(), null, minimum);
    }

    private static String displayName(String id) {
        return id.replace('-', ' ')
            .replace('_', ' ');
    }

    private String selectedStackName(GuiTextField slotField) {
        if (mc == null || mc.thePlayer == null) return "join the bound world";
        final int index;
        try {
            index = ProfileAssetInput.inventorySlot(slotField.getText(), "slot");
        } catch (IllegalArgumentException ignored) {
            return "enter a slot from 0 to 35";
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(index);
        return stack == null ? "slot " + index + " is empty" : "slot " + index + " - " + stack.getDisplayName();
    }

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass()
            .getSimpleName() : failure.getMessage();
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
    }

    private static final class Target {

        private final int dimension;
        private final int x;
        private final int y;
        private final int z;
        private final TileEntity tile;

        private Target(int dimension, int x, int y, int z, TileEntity tile) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tile = tile;
        }

        private NamedLocation location(String id, String displayName) {
            return new NamedLocation(id, displayName, dimension, x, y, z);
        }

        private String coordinates() {
            return "dim " + dimension + " / " + x + ", " + y + ", " + z;
        }
    }
}
