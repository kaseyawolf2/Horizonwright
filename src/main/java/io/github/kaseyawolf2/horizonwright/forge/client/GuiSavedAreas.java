package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;

/** Readable paginated view of all work areas saved in the active world profile. */
public final class GuiSavedAreas extends GuiScreen {

    private static final int BACK_BUTTON = 1;
    private static final int CLOSE_BUTTON = 2;
    private static final int PREVIOUS_BUTTON = 3;
    private static final int NEXT_BUTTON = 4;
    private static final int AREA_BUTTON_BASE = 100;
    private static final int AREAS_PER_PAGE = 6;

    private final GuiScreen parent;
    private final ProfileAssetEditorProvider editorProvider;
    private final List<GuiButton> areaButtons = new ArrayList<>();
    private GuiButton previousButton;
    private GuiButton nextButton;
    private List<NamedArea> areas = Collections.emptyList();
    private int left;
    private int top;
    private int panelWidth;
    private int page;
    private String detail = "Select a saved area to inspect its complete bounds.";

    public GuiSavedAreas(GuiScreen parent, ProfileAssetEditorProvider editorProvider) {
        if (parent == null || editorProvider == null) {
            throw new IllegalArgumentException("parent and editorProvider are required");
        }
        this.parent = parent;
        this.editorProvider = editorProvider;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(460, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(8, (height - 300) / 2);
        reload();
        areaButtons.clear();
        for (int index = 0; index < AREAS_PER_PAGE; index++) {
            GuiButton button = new GuiButton(
                AREA_BUTTON_BASE + index,
                left + 18,
                top + 44 + index * 25,
                panelWidth - 36,
                20,
                "");
            areaButtons.add(button);
            buttonList.add(button);
        }
        previousButton = new GuiButton(PREVIOUS_BUTTON, left + 18, top + 202, 76, 20, "Previous");
        nextButton = new GuiButton(NEXT_BUTTON, left + 100, top + 202, 76, 20, "Next");
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        buttonList.add(new GuiButton(CLOSE_BUTTON, left + 18, top + 270, 70, 20, "Close"));
        buttonList.add(new GuiButton(BACK_BUTTON, left + panelWidth - 88, top + 270, 70, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK_BUTTON) {
            mc.displayGuiScreen(parent);
        } else if (button.id == CLOSE_BUTTON) {
            mc.displayGuiScreen(null);
        } else if (button.id == PREVIOUS_BUTTON) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT_BUTTON) {
            page++;
        } else if (button.id >= AREA_BUTTON_BASE && button.id < AREA_BUTTON_BASE + AREAS_PER_PAGE) {
            int index = page * AREAS_PER_PAGE + button.id - AREA_BUTTON_BASE;
            if (index < areas.size()) detail = completeDescription(areas.get(index));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        configureButtons();
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + 300, 0xEE10141B);
        drawCenteredString(fontRendererObj, "Saved work areas", width / 2, top + 15, 0xFFF0C674);
        drawCenteredString(
            fontRendererObj,
            areas.size() + " area(s) in this world profile",
            width / 2,
            top + 29,
            0xFF8FAAD0);
        fontRendererObj.drawSplitString(detail, left + 18, top + 230, panelWidth - 36, 0xFFB8C8DE);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void reload() {
        ProfileAssetEditor editor = editorProvider.getCurrentProfileAssetEditor()
            .orElse(null);
        areas = editor == null ? Collections.<NamedArea>emptyList()
            : editor.load()
                .getNamedAreas();
    }

    private void configureButtons() {
        int pages = Math.max(1, (areas.size() + AREAS_PER_PAGE - 1) / AREAS_PER_PAGE);
        page = Math.min(page, pages - 1);
        int first = page * AREAS_PER_PAGE;
        for (int index = 0; index < areaButtons.size(); index++) {
            GuiButton button = areaButtons.get(index);
            int areaIndex = first + index;
            if (areaIndex < areas.size()) {
                NamedArea area = areas.get(areaIndex);
                button.displayString = area.getId() + " — " + shortBounds(area);
                button.visible = true;
                button.enabled = true;
            } else {
                button.visible = false;
                button.enabled = false;
            }
        }
        previousButton.enabled = page > 0;
        nextButton.enabled = page + 1 < pages;
    }

    private static String shortBounds(NamedArea area) {
        return area.getMinimum()
            .getX() + ","
            + area.getMinimum()
                .getY()
            + ","
            + area.getMinimum()
                .getZ()
            + " to "
            + area.getMaximum()
                .getX()
            + ","
            + area.getMaximum()
                .getY()
            + ","
            + area.getMaximum()
                .getZ();
    }

    static String completeDescription(NamedArea area) {
        return area.getId() + "\nDimension "
            + area.getMinimum()
                .getDimensionId()
            + "\nMinimum: "
            + area.getMinimum()
                .getX()
            + ", "
            + area.getMinimum()
                .getY()
            + ", "
            + area.getMinimum()
                .getZ()
            + "\nMaximum: "
            + area.getMaximum()
                .getX()
            + ", "
            + area.getMaximum()
                .getY()
            + ", "
            + area.getMaximum()
                .getZ();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
