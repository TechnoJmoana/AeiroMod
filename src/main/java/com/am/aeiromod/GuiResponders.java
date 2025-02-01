package com.am.aeiromod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AeiroMod GUI with two separate pages:
 * - Page 0: Autoresponders
 * - Page 1: Other Settings (carryMode, autoCarryMode, Bio, auto-remove semicolons)
 * Toggle between pages using a button.
 */
public class GuiResponders extends GuiScreen {
    private final AeiroMod parentMod;

    // Current page: 0 for Autoresponders, 1 for Other Settings
    private int currentPage = 0;

    // Responder-related components
    private List<Map.Entry<String, AeiroMod.ResponderData>> respondersList;
    // maxVisibleResponders is now dynamic (computed in initGui)
    private int maxVisibleResponders;
    private int scrollOffset = 0;
    private final List<GuiButton> responderButtons = new ArrayList<GuiButton>();

    // Other Settings components
    private GuiButton btnCarryMode;
    private GuiButton btnAutoCarry;
    private GuiTextField bioField;
    private GuiButton btnBioUpdate;

    // Add/Remove/Update Responder Buttons
    private GuiButton btnAddResponder;
    private GuiButton btnRemoveResponder;
    private GuiButton btnUpdateResponder;

    // Responder Phrase and Response Fields
    private GuiTextField responderPhraseField;
    private GuiTextField responderResponseField;

    // Toggle Page Button
    private GuiButton btnTogglePage;

    // "Close" button
    private GuiButton btnClose;

    // Toggle Auto-remove Semicolons Button
    private GuiButton btnToggleSemicolons;

    // Selected Responder Index (-1 if none selected)
    private int selectedResponderIndex = -1;

    // Layout constants for adaptive GUI
    private final int marginTop = 60;      // Top margin for the responder list
    private final int marginBottom = 150;  // Bottom margin for text fields and buttons
    private final int rowHeight = 22;      // Height for each responder row

    public GuiResponders(AeiroMod mod) {
        this.parentMod = mod;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        refreshList(); // Ensure respondersList is up-to-date

        int guiWidth = this.width;
        int guiHeight = this.height;

        // Compute dynamic maximum visible responders based on available space.
        maxVisibleResponders = (guiHeight - marginBottom - marginTop) / rowHeight;
        if (maxVisibleResponders < 1) {
            maxVisibleResponders = 1;
        }

        // Common Close Button
        btnClose = new GuiButton(999, guiWidth - 60, guiHeight - 30, 50, 20, "Close");
        this.buttonList.add(btnClose);

        // Toggle Page Button
        String toggleLabel = currentPage == 0 ? "Page 2" : "Page 1";
        btnTogglePage = new GuiButton(998, guiWidth - 120, guiHeight - 30, 60, 20, toggleLabel);
        this.buttonList.add(btnTogglePage);

        if (currentPage == 0) {
            setupAutorespondersPage(guiWidth, guiHeight);
        } else {
            setupOtherSettingsPage(guiWidth, guiHeight);
        }
    }

    /**
     * Sets up the Autoresponders page.
     */
    private void setupAutorespondersPage(int guiWidth, int guiHeight) {
        // Create responder buttons dynamically based on maxVisibleResponders.
        responderButtons.clear();
        for (int i = 0; i < maxVisibleResponders; i++) {
            GuiButton b = new GuiButton(200 + i, guiWidth - 80, marginTop + (i * rowHeight), 60, 20, "???");
            this.buttonList.add(b);
            responderButtons.add(b);
        }

        // Initialize responder buttons with correct labels based on scrollOffset.
        for (int i = 0; i < responderButtons.size(); i++) {
            int responderIndex = scrollOffset + i;
            if (responderIndex < respondersList.size()) {
                Map.Entry<String, AeiroMod.ResponderData> ent = respondersList.get(responderIndex);
                updateResponderButton(responderButtons.get(i), ent.getKey(), ent.getValue());
                responderButtons.get(i).visible = true;
            } else {
                responderButtons.get(i).visible = false;
            }
        }

        // Position text fields relative to the bottom margin.
        responderPhraseField = new GuiTextField(301, this.fontRendererObj, 20, guiHeight - marginBottom + 20, 200, 18);
        responderPhraseField.setMaxStringLength(100);
        responderPhraseField.setText("");
        responderPhraseField.setEnableBackgroundDrawing(true);
        responderPhraseField.setCanLoseFocus(true);
        responderPhraseField.setTextColor(0xFFFFFF);

        responderResponseField = new GuiTextField(302, this.fontRendererObj, 230, guiHeight - marginBottom + 20, 200, 18);
        responderResponseField.setMaxStringLength(200);
        responderResponseField.setText("");
        responderResponseField.setEnableBackgroundDrawing(true);
        responderResponseField.setCanLoseFocus(true);
        responderResponseField.setTextColor(0xFFFFFF);

        // Add/Remove/Update Responder Buttons positioned relative to the bottom margin.
        btnAddResponder = new GuiButton(303, 20, guiHeight - marginBottom + 60, 120, 20, "Add Responder");
        btnRemoveResponder = new GuiButton(304, 150, guiHeight - marginBottom + 60, 150, 20, "Remove Responder");
        btnUpdateResponder = new GuiButton(305, 310, guiHeight - marginBottom + 60, 150, 20, "Update Responder");
        this.buttonList.add(btnAddResponder);
        this.buttonList.add(btnRemoveResponder);
        this.buttonList.add(btnUpdateResponder);
    }

    /**
     * Sets up the Other Settings page.
     */
    private void setupOtherSettingsPage(int guiWidth, int guiHeight) {
        // Toggle Carry Modes
        int carryY = 60;
        btnCarryMode = new GuiButton(306, 20, carryY, 150, 20, "");
        btnAutoCarry = new GuiButton(307, 180, carryY, 150, 20, "");
        this.buttonList.add(btnCarryMode);
        this.buttonList.add(btnAutoCarry);
        updateCarryButtons();

        // Toggle Auto-remove Semicolons
        btnToggleSemicolons = new GuiButton(308, 340, carryY, 200, 20, "Auto-remove semicolons: " +
                (parentMod.isAutoRemoveSemicolons() ? "ON" : "OFF"));
        this.buttonList.add(btnToggleSemicolons);

        // Bio field near the bottom
        int bioY = guiHeight - 60;
        bioField = new GuiTextField(309, this.fontRendererObj, 20, bioY, guiWidth - 240, 18);
        bioField.setMaxStringLength(200);
        bioField.setText(parentMod.getUserBio());
        bioField.setEnableBackgroundDrawing(true);
        bioField.setCanLoseFocus(true);

        btnBioUpdate = new GuiButton(310, guiWidth - 220, bioY, 60, 20, "Set Bio");
        this.buttonList.add(btnBioUpdate);
    }

    /**
     * Refreshes the responders list from the parent mod.
     */
    private void refreshList() {
        respondersList = new ArrayList<Map.Entry<String, AeiroMod.ResponderData>>(parentMod.getCustomResponders().entrySet());
    }

    /**
     * Updates the carry mode buttons based on the current settings.
     */
    private void updateCarryButtons() {
        if (parentMod.isCarryMode()) {
            btnCarryMode.displayString = "CarryMode: ON";
        } else {
            btnCarryMode.displayString = "CarryMode: OFF";
        }
        if (parentMod.isAutoCarryMode()) {
            btnAutoCarry.displayString = "AutoCarry: ON";
        } else {
            btnAutoCarry.displayString = "AutoCarry: OFF";
        }
    }

    /**
     * Updates a responder button's display string based on its mode.
     *
     * @param button The button to update.
     * @param phrase The responder phrase.
     * @param rd     The responder data.
     */
    private void updateResponderButton(GuiButton button, String phrase, AeiroMod.ResponderData rd) {
        if (rd.mode == 0) {
            button.displayString = "DIS";
        } else if (rd.mode == 1) {
            button.displayString = "EN";
        } else if (rd.mode == 2) {
            button.displayString = "CAR";
        } else {
            button.displayString = "???";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        // Close Button
        if (button == btnClose) {
            this.mc.displayGuiScreen(null);
            return;
        }
        // Toggle Page Button
        if (button == btnTogglePage) {
            currentPage = (currentPage + 1) % 2; // Toggle between 0 and 1
            this.initGui(); // Reinitialize GUI for the new page
            return;
        }
        if (currentPage == 0) { // Autoresponders Page
            handleAutorespondersPage(button);
        } else { // Other Settings Page
            handleOtherSettingsPage(button);
        }
    }

    /**
     * Handles button actions on the Autoresponders page.
     */
    private void handleAutorespondersPage(GuiButton button) {
        if (button.id >= 200 && button.id < 200 + maxVisibleResponders) {
            int line = button.id - 200;
            int responderIndex = scrollOffset + line;
            if (responderIndex >= 0 && responderIndex < respondersList.size()) {
                Map.Entry<String, AeiroMod.ResponderData> ent = respondersList.get(responderIndex);
                AeiroMod.ResponderData rd = ent.getValue();
                // Cycle: 0->1->2->0
                rd.mode = (rd.mode + 1) % 3;
                parentMod.getCustomResponders().put(ent.getKey(), rd);
                parentMod.saveConfig();
                updateResponderButton(button, ent.getKey(), rd);
                // Update GUI to refresh button labels
                this.initGui();
            }
        }
        // Add Responder
        else if (button == btnAddResponder) {
            String phrase = responderPhraseField.getText().trim().toLowerCase();
            String response = responderResponseField.getText().trim();
            if (phrase.isEmpty() || response.isEmpty()) {
                parentMod.setGuiNotification("Phrase and Response cannot be empty.");
                return;
            }
            if (parentMod.getCustomResponders().containsKey(phrase)) {
                parentMod.setGuiNotification("Responder for '" + phrase + "' already exists.");
                return;
            }
            parentMod.setResponder(phrase, response);
            parentMod.setGuiNotification("Added responder: [" + phrase + "] => [" + response + "]");
            refreshList();
            this.initGui(); // Refresh GUI
        }
        // Remove Responder
        else if (button == btnRemoveResponder) {
            if (selectedResponderIndex == -1) {
                parentMod.setGuiNotification("No responder selected to remove.");
                return;
            }
            if (selectedResponderIndex >= respondersList.size()) {
                parentMod.setGuiNotification("Selected responder index is out of range.");
                return;
            }
            String phrase = respondersList.get(selectedResponderIndex).getKey();
            parentMod.removeResponder(phrase);
            parentMod.setGuiNotification("Removed responder: [" + phrase + "]");
            selectedResponderIndex = -1; // Reset selection
            refreshList();
            this.initGui(); // Refresh GUI
        }
        // Update Responder
        else if (button == btnUpdateResponder) {
            if (selectedResponderIndex == -1) {
                parentMod.setGuiNotification("No responder selected to update.");
                return;
            }
            if (selectedResponderIndex >= respondersList.size()) {
                parentMod.setGuiNotification("Selected responder index is out of range.");
                return;
            }
            String phrase = respondersList.get(selectedResponderIndex).getKey();
            String newResponse = responderResponseField.getText().trim();
            if (newResponse.isEmpty()) {
                parentMod.setGuiNotification("Response cannot be empty.");
                return;
            }
            parentMod.setResponder(phrase, newResponse);
            parentMod.setGuiNotification("Updated responder: [" + phrase + "] => [" + newResponse + "]");
            refreshList();
            this.initGui(); // Refresh GUI
        }
    }

    /**
     * Handles button actions on the Other Settings page.
     */
    private void handleOtherSettingsPage(GuiButton button) {
        if (button == btnCarryMode) {
            boolean old = parentMod.isCarryMode();
            parentMod.setCarryMode(!old, true); // Toggle and notify
            updateCarryButtons();
        } else if (button == btnAutoCarry) {
            boolean old = parentMod.isAutoCarryMode();
            parentMod.setAutoCarryMode(!old, true); // Toggle and notify
            updateCarryButtons();
        }
        // Toggle Auto-remove Semicolons
        else if (button == btnToggleSemicolons) {
            parentMod.setAutoRemoveSemicolons(!parentMod.isAutoRemoveSemicolons());
            // Update button label
            btnToggleSemicolons.displayString = "Auto-remove semicolons: " +
                    (parentMod.isAutoRemoveSemicolons() ? "ON" : "OFF");
        }
        // Bio Update
        else if (button == btnBioUpdate) {
            String newBio = bioField.getText().trim();
            parentMod.setUserBio(newBio);
            parentMod.setGuiNotification("Bio updated.");
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        if (currentPage == 0) { // Autoresponders Page
            responderPhraseField.textboxKeyTyped(typedChar, keyCode);
            responderResponseField.textboxKeyTyped(typedChar, keyCode);
        } else { // Other Settings Page
            bioField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (currentPage == 0) { // Autoresponders Page
            responderPhraseField.mouseClicked(mouseX, mouseY, mouseButton);
            responderResponseField.mouseClicked(mouseX, mouseY, mouseButton);
        } else { // Other Settings Page
            bioField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        // Handle responder selection on Autoresponders Page
        if (currentPage == 0) {
            for (int i = 0; i < respondersList.size() && i < maxVisibleResponders; i++) {
                int responderIndex = scrollOffset + i;
                if (responderIndex >= respondersList.size()) continue;
                int xStart = 20;
                int yStart = marginTop + (i * rowHeight);
                int xEnd = this.width - 80;
                int yEnd = yStart + 20;
                if (mouseX >= xStart && mouseX <= xEnd && mouseY >= yStart && mouseY <= yEnd) {
                    selectedResponderIndex = responderIndex;
                    break;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && currentPage == 0) { // Only scroll on Autoresponders Page
            if (wheel > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                selectedResponderIndex = -1; // Reset selection on scroll
            } else {
                int max = respondersList.size() - maxVisibleResponders;
                if (max < 0) max = 0;
                scrollOffset = Math.min(max, scrollOffset + 1);
                selectedResponderIndex = -1; // Reset selection on scroll
            }
            this.initGui(); // Refresh GUI to reflect scroll
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (currentPage == 0) { // Autoresponders Page
            responderPhraseField.updateCursorCounter();
            responderResponseField.updateCursorCounter();
        } else { // Other Settings Page
            bioField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (currentPage == 0) { // Autoresponders Page
            drawAutorespondersPage(mouseX, mouseY, partialTicks);
        } else { // Other Settings Page
            drawOtherSettingsPage(mouseX, mouseY, partialTicks);
        }
        // Draw the GUI notification if it exists
        if (parentMod.guiNotification != null && !parentMod.guiNotification.isEmpty()) {
            int x = 20;
            int y = 20;
            int width = 300;
            int height = 60;
            drawRect(x - 5, y - 5, x + width, y + height, 0x80000000); // Semi-transparent background
            String[] lines = parentMod.guiNotification.split("\n");
            for (int i = 0; i < lines.length; i++) {
                this.fontRendererObj.drawString(lines[i], x, y + i * 10, 0xFFFFFF);
            }
        }
    }

    /**
     * Draws the Autoresponders page.
     */
    private void drawAutorespondersPage(int mouseX, int mouseY, float partialTicks) {
        // Title
        String title = "AeiroMod - Autoresponders";
        int tw = this.fontRendererObj.getStringWidth(title);
        this.fontRendererObj.drawString(title, (this.width - tw) / 2, 10, 0xFFFFFF);
        // Responders label
        this.fontRendererObj.drawString("Responders (scroll w/ wheel):", 20, marginTop - 20, 0xFFFFAA);
        // Show up to maxVisibleResponders lines
        int start = scrollOffset;
        int end = start + maxVisibleResponders;
        if (end > respondersList.size()) end = respondersList.size();
        for (int i = start; i < end; i++) {
            Map.Entry<String, AeiroMod.ResponderData> ent = respondersList.get(i);
            AeiroMod.ResponderData rd = ent.getValue();
            int lineIndex = i - start;
            int drawY = marginTop + (lineIndex * rowHeight);
            // Highlight selected responder
            if (i == selectedResponderIndex) {
                drawRect(15, drawY, this.width - 80, drawY + 20, 0x6000FF00); // Semi-transparent green
            }
            // Update button positions and labels
            GuiButton b = responderButtons.get(lineIndex);
            b.yPosition = drawY;
            b.xPosition = this.width - 80;
            updateResponderButton(b, ent.getKey(), rd);
            // Draw responder info text (e.g., "1) phrase => response")
            String showLine = (i + 1) + ") " + ent.getKey() + " => " + rd.response;
            this.fontRendererObj.drawString(showLine, 20, drawY + 6, 0xE0E0E0);
        }
        // Draw the responder text fields and their labels.
        responderPhraseField.drawTextBox();
        responderResponseField.drawTextBox();
        this.fontRendererObj.drawString("Phrase:", 20, this.height - marginBottom, 0xFFFFFF);
        this.fontRendererObj.drawString("Response:", 230, this.height - marginBottom, 0xFFFFFF);
        this.fontRendererObj.drawString("Responder Management:", 20, this.height - marginBottom - 20, 0xFFFFAA);
    }

    /**
     * Draws the Other Settings page.
     */
    private void drawOtherSettingsPage(int mouseX, int mouseY, float partialTicks) {
        // Title
        String title = "AeiroMod - Settings";
        int tw = this.fontRendererObj.getStringWidth(title);
        this.fontRendererObj.drawString(title, (this.width - tw) / 2, 10, 0xFFFFFF);
        // Toggle Carry Modes
        int carryY = 60;
        this.fontRendererObj.drawString("Toggle Carry Modes:", 20, carryY - 15, 0xA0A0FF);
        btnCarryMode.drawButton(this.mc, mouseX, mouseY);
        btnAutoCarry.drawButton(this.mc, mouseX, mouseY);
        // Toggle Auto-remove Semicolons
        btnToggleSemicolons.drawButton(this.mc, mouseX, mouseY);
        // Bio label and field
        int bioY = this.height - 60;
        this.fontRendererObj.drawString("Bio:", 20, bioY - 12, 0xFFFFFF);
        bioField.drawTextBox();
        btnBioUpdate.drawButton(this.mc, mouseX, mouseY);
    }

    /**
     * Retrieves the string representation of the responder mode.
     *
     * @param mode The mode integer.
     * @return The mode as a string.
     */
    private String getModeString(int mode) {
        switch (mode) {
            case 0:
                return "DISABLED";
            case 1:
                return "ENABLED";
            case 2:
                return "CARRY-ONLY";
            default:
                return "UNKNOWN";
        }
    }
}