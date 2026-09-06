package io.github.kaseyawolf2.horizonwright.forge.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import io.github.kaseyawolf2.horizonwright.core.base.AreaKind;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetUpdate;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.session.CurrentRuntimeProvider;

/** An area's purpose, bounds, and output destination live together. */
public final class GuiAreaSettings extends GuiReadableScreen {

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editors;
    private final CurrentRuntimeProvider runtime;
    private NamedArea area;
    private AreaKind kind;
    private String storage;
    private String message = "Choose the area type, then save. A blank chest override uses the Base default.";
    private GuiButton typeButton, chestButton;
    private int left, top;

    public GuiAreaSettings(GuiScreen parent, ProfileAssetEditorProvider editors, CurrentRuntimeProvider runtime,
        NamedArea area) {
        this.parent = parent;
        this.editors = editors;
        this.runtime = runtime;
        this.area = area;
        kind = area.getKind();
        storage = area.getStorageId();
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        left = (width - 500) / 2;
        top = (height - 350) / 2;
        ProfileAssetEditor editor = editors.getCurrentProfileAssetEditor()
            .orElse(null);
        if (editor != null) for (NamedArea saved : editor.load()
            .getNamedAreas())
            if (saved.getId()
                .equals(area.getId())) area = saved;
        typeButton = new GuiHorizonwrightButton(1, left + 150, top + 56, 332, 20, kind.getLabel());
        buttonList.add(typeButton);
        chestButton = new GuiHorizonwrightButton(2, left + 150, top + 96, 332, 20, chestLabel());
        buttonList.add(chestButton);
        add(3, "Use targeted chest for this area", 132);
        add(4, "Edit name and bounds", 166);
        add(5, "Save area settings", 206);
        add(6, "Configure " + kind.getLabel(), 238);
        buttonList.add(new GuiHorizonwrightButton(0, left + 402, top + 316, 80, 20, "Back"));
    }

    private String chestLabel() {
        return storage == null ? "Use default chest" : storage;
    }

    private void add(int id, String label, int y) {
        buttonList.add(new GuiHorizonwrightButton(id, left + 18, top + y, 464, 20, label));
    }

    private ProfileAssetEditor editor() {
        return editors.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("Join the bound world first."));
    }

    private NamedArea latest() {
        for (NamedArea saved : editor().load()
            .getNamedAreas())
            if (saved.getId()
                .equals(area.getId())) return saved;
        throw new IllegalStateException("This area no longer exists.");
    }

    private void save() {
        if (storage != null) {
            boolean found = false;
            for (NamedStorageEndpoint chest : editor().load()
                .getNamedStorageEndpoints())
                if (chest.getId()
                    .equals(storage)) found = true;
            if (!found) throw new IllegalStateException("The selected chest no longer exists.");
        }
        area = latest().withSettings(kind, storage);
        editor().apply(ProfileAssetUpdate.ofArea(area));
        message = "Area settings saved.";
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
            return;
        }
        try {
            if (button.id == 1) {
                kind = kind.next();
                typeButton.displayString = kind.getLabel();
                for (Object value : buttonList)
                    if (((GuiButton) value).id == 6) ((GuiButton) value).displayString = "Configure " + kind.getLabel();
            }
            if (button.id == 2) {
                java.util.List<NamedStorageEndpoint> chests = editor().load()
                    .getNamedStorageEndpoints();
                int index = -1;
                for (int i = 0; i < chests.size(); i++) if (chests.get(i)
                    .getId()
                    .equals(storage)) index = i;
                storage = index + 1 < chests.size() ? chests.get(index + 1)
                    .getId() : null;
                chestButton.displayString = chestLabel();
            }
            if (button.id == 3) {
                String id = "area-" + area.getId() + "-chest";
                BaseAssetCapture.chest(mc, editor(), id);
                storage = id;
                save();
                chestButton.displayString = chestLabel();
            }
            if (button.id == 4) {
                save();
                mc.displayGuiScreen(new GuiSavedAreaEditor(this, editors, runtime, area));
            }
            if (button.id == 5) save();
            if (button.id == 6) {
                if (kind == AreaKind.UNASSIGNED) throw new IllegalArgumentException("Choose an area type first.");
                save();
                switch (kind) {
                    case FARM:
                        mc.displayGuiScreen(new GuiSavedAreaEditor(this, editors, runtime, area));
                        break;
                    case LIVESTOCK:
                        mc.displayGuiScreen(new GuiHusbandrySetup(this, runtime, editors, area));
                        break;
                    case TREE_FARM:
                        mc.displayGuiScreen(new GuiTreeFarmSetup(this, runtime, area));
                        break;
                    case EXCAVATION:
                        mc.displayGuiScreen(new GuiExcavationSetup(this, runtime, editors, area));
                        break;
                    case QUARRY:
                        mc.displayGuiScreen(new GuiManagedQuarrySetup(this, runtime, editors, area));
                        break;
                    default:
                        break;
                }
            }
        } catch (RuntimeException failure) {
            message = "Could not apply: " + failure.getMessage();
        }
    }

    @Override
    protected void drawContents(int mx, int my, float ticks) {
        drawDefaultBackground();
        drawRect(left, top, left + 500, top + 350, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Area: " + area.getDisplayName(), width / 2, top + 14, 0xFFF0C674);
        drawString(fontRendererObj, "Type", left + 18, top + 62, 0xFFB8C8DE);
        drawString(fontRendererObj, "Output chest", left + 18, top + 102, 0xFFB8C8DE);
        if (kind != AreaKind.EXCAVATION && kind != AreaKind.QUARRY && kind != AreaKind.UNASSIGNED) drawString(
            fontRendererObj,
            "Chest saved here; automatic delivery for this type is not available yet.",
            left + 18,
            top + 120,
            0xFFFFAA66);
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
