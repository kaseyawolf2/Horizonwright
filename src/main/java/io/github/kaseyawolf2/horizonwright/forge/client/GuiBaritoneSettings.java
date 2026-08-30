package io.github.kaseyawolf2.horizonwright.forge.client;

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
    private static final int SETTINGS_PER_PAGE = 7;

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
    private String status = "Select a setting to inspect or edit it.";
    private int page;
    private int left;
    private int top;
    private int panelWidth;

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
        panelWidth = Math.min(560, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(10, (height - 330) / 2);
        buttonList.clear();
        buttonList.add(new GuiButton(BACK_TAB, left + 12, top + 10, 92, 20, "Dashboard"));
        GuiButton selectedTab = new GuiButton(BARITONE_TAB, left + 110, top + 10, 110, 20, "Baritone config");
        selectedTab.enabled = false;
        buttonList.add(selectedTab);

        searchField = new GuiTextField(fontRendererObj, left + 12, top + 42, panelWidth - 24, 18);
        searchField.setMaxStringLength(120);
        searchField.setFocused(true);

        for (int index = 0; index < SETTINGS_PER_PAGE; index++) {
            buttonList.add(
                new GuiButton(SETTING_BUTTON_BASE + index, left + 12, top + 68 + index * 22, panelWidth - 24, 20, ""));
        }
        previousButton = new GuiButton(PREVIOUS_PAGE, left + 12, top + 226, 72, 20, "Previous");
        nextButton = new GuiButton(NEXT_PAGE, left + 90, top + 226, 72, 20, "Next");
        applyButton = new GuiButton(APPLY_VALUE, left + panelWidth - 230, top + 294, 66, 20, "Apply");
        resetButton = new GuiButton(RESET_VALUE, left + panelWidth - 158, top + 294, 66, 20, "Reset");
        toggleButton = new GuiButton(TOGGLE_VALUE, left + panelWidth - 86, top + 294, 74, 20, "Toggle");
        buttonList.add(previousButton);
        buttonList.add(nextButton);
        buttonList.add(applyButton);
        buttonList.add(resetButton);
        buttonList.add(toggleButton);

        valueField = new GuiTextField(fontRendererObj, left + 12, top + 270, panelWidth - 24, 18);
        valueField.setMaxStringLength(4096);
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
        if (button.id >= SETTING_BUTTON_BASE && button.id < SETTING_BUTTON_BASE + SETTINGS_PER_PAGE) {
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
                status = "Reset " + changed.getName() + " to its default.";
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
        drawRect(left, top, left + panelWidth, top + 322, 0xE010141B);
        drawCenteredString(fontRendererObj, "Baritone configuration", width / 2, top + 16, 0xFFF0C674);
        if (searchField.getText()
            .isEmpty() && !searchField.isFocused()) {
            drawString(fontRendererObj, "Search settings...", left + 16, top + 47, 0xFF777777);
        }
        searchField.drawTextBox();
        int pageCount = Math.max(1, (matches.size() + SETTINGS_PER_PAGE - 1) / SETTINGS_PER_PAGE);
        drawString(
            fontRendererObj,
            matches.size() + " settings  |  page " + (page + 1) + "/" + pageCount,
            left + 170,
            top + 232,
            0xFFB8C8DE);
        BaritoneSettingsCatalog.Entry selected = selectedEntry();
        if (selected == null) {
            drawString(fontRendererObj, "No setting selected", left + 12, top + 253, 0xFFAAAAAA);
        } else {
            String flags = selected.isJavaOnly() ? "  [Java-only, read-only]" : "";
            drawString(
                fontRendererObj,
                truncate(selected.getName() + " : " + selected.getType() + flags, 80),
                left + 12,
                top + 253,
                selected.isJavaOnly() ? 0xFFFFAA66 : 0xFFB8C8DE);
        }
        valueField.drawTextBox();
        drawString(fontRendererObj, truncate(status, 84), left + 12, top + 299, 0xFFB8C8DE);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshMatches() {
        matches = catalog.search(searchField == null ? "" : searchField.getText());
        int pageCount = Math.max(1, (matches.size() + SETTINGS_PER_PAGE - 1) / SETTINGS_PER_PAGE);
        page = Math.max(0, Math.min(page, pageCount - 1));
        refreshButtons();
    }

    private void refreshButtons() {
        int start = page * SETTINGS_PER_PAGE;
        for (int index = 0; index < SETTINGS_PER_PAGE; index++) {
            GuiButton button = button(SETTING_BUTTON_BASE + index);
            int matchIndex = start + index;
            boolean visible = matchIndex < matches.size();
            button.visible = visible;
            button.enabled = visible;
            if (visible) {
                BaritoneSettingsCatalog.Entry entry = matches.get(matchIndex);
                button.displayString = truncate(
                    entry.getName() + " = " + entry.getCurrentValue() + (entry.isJavaOnly() ? "  [read-only]" : ""),
                    78);
            }
        }
        previousButton.enabled = page > 0;
        nextButton.enabled = (page + 1) * SETTINGS_PER_PAGE < matches.size();
        refreshEditorControls();
    }

    private void selectVisible(int visibleIndex) {
        int matchIndex = page * SETTINGS_PER_PAGE + visibleIndex;
        if (matchIndex < 0 || matchIndex >= matches.size()) {
            return;
        }
        BaritoneSettingsCatalog.Entry entry = matches.get(matchIndex);
        selectedName = entry.getName();
        valueField.setText(entry.getCurrentValue());
        valueField.setFocused(!entry.isJavaOnly());
        status = "Default: " + entry.getDefaultValue();
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

    private GuiButton button(int id) {
        for (Object candidate : buttonList) {
            GuiButton guiButton = (GuiButton) candidate;
            if (guiButton.id == id) {
                return guiButton;
            }
        }
        throw new IllegalStateException("missing GUI button " + id);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.trim()
            .isEmpty() ? failure.getClass()
                .getSimpleName() : message;
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }
}
