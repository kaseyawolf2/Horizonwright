package io.github.kaseyawolf2.horizonwright.forge.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneSettingsCatalog;

/** Searchable dashboard tab for every setting exposed by the installed Baritone build. */
public final class GuiBaritoneSettings extends GuiScreen {

    private static final int BACK_TAB = 1;
    private static final int BARITONE_TAB = 2;
    private static final int PREVIOUS_PAGE = 3;
    private static final int NEXT_PAGE = 4;
    private static final int APPLY_VALUE = 5;
    private static final int RESET_VALUE = 6;
    private static final int TOGGLE_VALUE = 7;
    private static final int SETTING_BUTTON_BASE = 100;

    private final GuiScreen dashboard;
    private final BaritoneSettingsCatalog catalog;
    private GuiTextField searchField;
    private GuiTextField valueField;
    private GuiButton previousButton;
    private GuiButton nextButton;
    private GuiButton applyButton;
    private GuiButton resetButton;
    private GuiButton toggleButton;
    private List<BaritoneSettingsCatalog.Entry> matches = Collections.emptyList();
    private String selectedName;
    private String status = "Select a setting on the left.";
    private int page;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listWidth;
    private int detailsLeft;
    private int detailsWidth;
    private int contentTop;
    private int settingsPerPage;

    public GuiBaritoneSettings(GuiScreen dashboard) {
        this(dashboard, BaritoneSettingsCatalog.installed());
    }

    GuiBaritoneSettings(GuiScreen dashboard, BaritoneSettingsCatalog catalog) {
        if (dashboard == null || catalog == null) {
            throw new IllegalArgumentException("dashboard and catalog are required");
        }
        this.dashboard = dashboard;
        this.catalog = catalog;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(900, width - 24);
        panelHeight = Math.min(520, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        contentTop = top + 94;
        listWidth = Math.max(120, Math.min(360, (panelWidth - 40) / 2));
        detailsLeft = left + 20 + listWidth;
        detailsWidth = left + panelWidth - 12 - detailsLeft;
        settingsPerPage = Math.max(1, Math.min(14, (panelHeight - 184) / 22));

        buttonList.clear();
        buttonList.add(new GuiButton(BACK_TAB, left + 12, top + 38, 92, 20, "Dashboard"));
        GuiButton selectedTab = new GuiButton(BARITONE_TAB, left + 110, top + 38, 110, 20, "Baritone config");
        selectedTab.enabled = false;
        buttonList.add(selectedTab);

        searchField = new GuiTextField(fontRendererObj, left + 12, top + 66, panelWidth - 24, 18);
        searchField.setMaxStringLength(120);
        searchField.setFocused(true);

        for (int index = 0; index < settingsPerPage; index++) {
            buttonList
                .add(new GuiButton(SETTING_BUTTON_BASE + index, left + 12, contentTop + index * 22, listWidth, 20, ""));
        }

        int pagerY = contentTop + settingsPerPage * 22 + 4;
        previousButton = new GuiButton(PREVIOUS_PAGE, left + 12, pagerY, 86, 20, "Previous");
        nextButton = new GuiButton(NEXT_PAGE, left + 104, pagerY, 86, 20, "Next");

        int editorY = top + panelHeight - 58;
        valueField = new GuiTextField(fontRendererObj, detailsLeft + 8, editorY, detailsWidth - 16, 18);
        valueField.setMaxStringLength(4096);
        int actionY = top + panelHeight - 30;
        int actionWidth = Math.max(38, (detailsWidth - 28) / 3);
        applyButton = new GuiButton(APPLY_VALUE, detailsLeft + 8, actionY, actionWidth, 20, "Apply");
        resetButton = new GuiButton(RESET_VALUE, detailsLeft + 12 + actionWidth, actionY, actionWidth, 20, "Reset");
        toggleButton = new GuiButton(
            TOGGLE_VALUE,
            detailsLeft + 16 + actionWidth * 2,
            actionY,
            actionWidth,
            20,
            "Toggle");
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        buttonList.add(applyButton);
        buttonList.add(resetButton);
        buttonList.add(toggleButton);

        refreshMatches();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        searchField.updateCursorCounter();
        valueField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK_TAB) {
            mc.displayGuiScreen(dashboard);
            return;
        }
        if (button.id == PREVIOUS_PAGE) {
            page = Math.max(0, page - 1);
            refreshButtons();
            return;
        }
        if (button.id == NEXT_PAGE) {
            page++;
            refreshButtons();
            return;
        }
        if (button.id >= SETTING_BUTTON_BASE && button.id < SETTING_BUTTON_BASE + settingsPerPage) {
            selectVisible(button.id - SETTING_BUTTON_BASE);
            return;
        }
        if (selectedName == null) {
            return;
        }
        try {
            BaritoneSettingsCatalog.Entry changed;
            if (button.id == APPLY_VALUE) {
                changed = catalog.apply(selectedName, valueField.getText());
                status = "Saved " + changed.getName() + ".";
            } else if (button.id == RESET_VALUE) {
                changed = catalog.reset(selectedName);
                status = "Reset to default: " + changed.getDefaultValue();
            } else if (button.id == TOGGLE_VALUE) {
                changed = catalog.toggle(selectedName);
                status = "Saved " + changed.getName() + ".";
            } else {
                return;
            }
            valueField.setText(changed.getCurrentValue());
            refreshMatches();
        } catch (RuntimeException failure) {
            status = "Not changed: " + safeMessage(failure);
        }
    }

    @Override
    protected void keyTyped(char typedCharacter, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(dashboard);
            return;
        }
        if (searchField.textboxKeyTyped(typedCharacter, keyCode)) {
            page = 0;
            refreshMatches();
            return;
        }
        if (valueField.textboxKeyTyped(typedCharacter, keyCode) && keyCode == Keyboard.KEY_RETURN) {
            actionPerformed(applyButton);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        valueField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xE010141B);
        drawCenteredString(fontRendererObj, "Baritone configuration", width / 2, top + 16, 0xFFF0C674);
        if (searchField.getText()
            .isEmpty() && !searchField.isFocused()) {
            drawString(fontRendererObj, "Search settings...", left + 16, top + 71, 0xFF777777);
        }
        searchField.drawTextBox();

        int pagerY = contentTop + settingsPerPage * 22 + 4;
        int pageCount = Math.max(1, (matches.size() + settingsPerPage - 1) / settingsPerPage);
        drawString(
            fontRendererObj,
            matches.size() + " settings  |  page " + (page + 1) + "/" + pageCount,
            left + 198,
            pagerY + 6,
            0xFFB8C8DE);

        drawRect(detailsLeft, contentTop, detailsLeft + detailsWidth, top + panelHeight - 66, 0x801A222D);
        BaritoneSettingsCatalog.Entry selected = selectedEntry();
        if (selected == null) {
            drawString(fontRendererObj, "Select a setting", detailsLeft + 10, contentTop + 10, 0xFFF0C674);
            drawString(
                fontRendererObj,
                fit("Its current value, default, and type will appear here.", detailsWidth - 20),
                detailsLeft + 10,
                contentTop + 28,
                0xFFAAAAAA);
        } else {
            int textWidth = detailsWidth - 20;
            drawString(
                fontRendererObj,
                fit(selected.getName(), textWidth),
                detailsLeft + 10,
                contentTop + 10,
                0xFFF0C674);
            drawString(
                fontRendererObj,
                fit("Type: " + selected.getType(), textWidth),
                detailsLeft + 10,
                contentTop + 30,
                0xFFB8C8DE);
            drawString(
                fontRendererObj,
                fit("Current: " + selected.getCurrentValue(), textWidth),
                detailsLeft + 10,
                contentTop + 50,
                0xFFFFFFFF);
            drawString(
                fontRendererObj,
                fit("Default: " + selected.getDefaultValue(), textWidth),
                detailsLeft + 10,
                contentTop + 70,
                0xFFAAAAAA);
            if (selected.isJavaOnly()) {
                drawString(
                    fontRendererObj,
                    "Java-only setting (read-only)",
                    detailsLeft + 10,
                    contentTop + 94,
                    0xFFFFAA66);
            }
        }
        drawString(
            fontRendererObj,
            fit(status, detailsWidth - 20),
            detailsLeft + 10,
            top + panelHeight - 80,
            0xFFB8C8DE);
        valueField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawSettingTooltip(mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshMatches() {
        matches = catalog.search(searchField == null ? "" : searchField.getText());
        int pageCount = Math.max(1, (matches.size() + settingsPerPage - 1) / settingsPerPage);
        page = Math.max(0, Math.min(page, pageCount - 1));
        refreshButtons();
    }

    private void refreshButtons() {
        int start = page * settingsPerPage;
        for (int index = 0; index < settingsPerPage; index++) {
            GuiButton button = button(SETTING_BUTTON_BASE + index);
            int matchIndex = start + index;
            boolean visible = matchIndex < matches.size();
            button.visible = visible;
            button.enabled = visible;
            if (visible) {
                BaritoneSettingsCatalog.Entry entry = matches.get(matchIndex);
                String suffix = entry.isJavaOnly() ? "  [read-only]"
                    : entry.isBooleanValue() ? " = " + entry.getCurrentValue() : "";
                button.displayString = fit(entry.getName() + suffix, listWidth - 12);
            }
        }
        previousButton.enabled = page > 0;
        nextButton.enabled = (page + 1) * settingsPerPage < matches.size();
        refreshEditorControls();
    }

    private void selectVisible(int visibleIndex) {
        int matchIndex = page * settingsPerPage + visibleIndex;
        if (matchIndex < 0 || matchIndex >= matches.size()) {
            return;
        }
        BaritoneSettingsCatalog.Entry entry = matches.get(matchIndex);
        selectedName = entry.getName();
        valueField.setText(entry.getCurrentValue());
        valueField.setFocused(!entry.isJavaOnly());
        status = entry.isJavaOnly() ? "This value is controlled by Java code." : "Edit below, then Apply.";
        refreshEditorControls();
    }

    private void refreshEditorControls() {
        BaritoneSettingsCatalog.Entry selected = selectedEntry();
        boolean editable = selected != null && !selected.isJavaOnly();
        valueField.setEnabled(editable);
        applyButton.enabled = editable;
        resetButton.enabled = editable;
        toggleButton.enabled = editable && selected.isBooleanValue();
    }

    private BaritoneSettingsCatalog.Entry selectedEntry() {
        if (selectedName == null) {
            return null;
        }
        for (BaritoneSettingsCatalog.Entry entry : catalog.search(selectedName)) {
            if (selectedName.equals(entry.getName())) {
                return entry;
            }
        }
        return null;
    }

    private void drawSettingTooltip(int mouseX, int mouseY) {
        int start = page * settingsPerPage;
        for (int index = 0; index < settingsPerPage; index++) {
            int matchIndex = start + index;
            if (matchIndex >= matches.size()) return;
            GuiButton button = button(SETTING_BUTTON_BASE + index);
            if (!button.visible || mouseX < button.xPosition
                || mouseX >= button.xPosition + button.width
                || mouseY < button.yPosition
                || mouseY >= button.yPosition + button.height) continue;
            BaritoneSettingsCatalog.Entry entry = matches.get(matchIndex);
            drawHoveringText(
                Arrays.asList(
                    entry.getName(),
                    "Type: " + entry.getType(),
                    "Current: " + entry.getCurrentValue(),
                    "Default: " + entry.getDefaultValue(),
                    entry.isJavaOnly() ? "Read-only: controlled by Java code" : "Click to inspect or edit"),
                mouseX,
                mouseY,
                fontRendererObj);
            return;
        }
    }

    private GuiButton button(int id) {
        for (Object candidate : buttonList) {
            GuiButton guiButton = (GuiButton) candidate;
            if (guiButton.id == id) {
                return guiButton;
            }
        }
        throw new IllegalStateException("missing GUI button " + id);
    }

    private String fit(String value, int maximumWidth) {
        if (value == null || fontRendererObj.getStringWidth(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && fontRendererObj.getStringWidth(value.substring(0, end) + ellipsis) > maximumWidth) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : message;
    }
}
