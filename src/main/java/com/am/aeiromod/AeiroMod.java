package com.am.aeiromod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * AeiroMod 1.8.9
 * Version 1.2.2
 *
 * Features:
 * - Chat-based commands: type e.g. "?carry ..." in chat and the mod replies.
 * - In-GUI notifications that disappear after ~2 seconds.
 * - Auto-remove semicolons feature.
 * - Delayed mod messages: AeiroMod's responses now appear one tick after your chat command.
 *
 * IMPORTANT: In older versions the client echoed its own messages so that ? commands
 * were processed via ClientChatReceivedEvent. In the current environment non-slash
 * messages are no longer echoed, so we replace the default chat screen with our custom
 * subclass (MyGuiChat) to intercept outgoing messages. Also, we force-close any active GUI
 * when opening the mod GUI.
 */
@Mod(
        modid = AeiroMod.MODID,
        name  = AeiroMod.NAME,
        version = AeiroMod.VERSION,
        clientSideOnly = true
)
public class AeiroMod {
    public static final String MODID   = "aeiromod";
    public static final String NAME    = "Aeiro Mod";
    public static final String VERSION = "1.2.2";

    // Configuration file name
    private static final String CONFIG_FILE_NAME = "aeiromod.cfg";

    // User bio
    private String userBio = "";

    // Custom responders (for auto-response commands)
    private LinkedHashMap<String, ResponderData> customResponders = new LinkedHashMap<String, ResponderData>();

    // Carry lists
    private final List<String>  clients       = new ArrayList<String>();
    private final List<String>  runDifficulty = new ArrayList<String>();
    private final List<Integer> runsCompleted = new ArrayList<Integer>();
    private final List<Integer> runsRequested = new ArrayList<Integer>();

    // Delayed chat queue for mod messages
    private final Queue<QueuedChatMessage> chatQueue = new LinkedList<QueuedChatMessage>();

    // Class to hold a queued chat message with a delay counter.
    private class QueuedChatMessage {
        String message;
        int delay;
        public QueuedChatMessage(String message, int delay) {
            this.message = message;
            this.delay = delay;
        }
    }

    // Reflection for capturing the chat input field
    private static Field guiChatInputField = null;
    private GuiTextField chatField = null;
    static {
        String[] possibleFields = {"inputField", "field_146409_v", "field_146408_f"};
        for (String fieldName : possibleFields) {
            try {
                guiChatInputField = GuiChat.class.getDeclaredField(fieldName);
                guiChatInputField.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) { }
        }
    }

    // For ?setresponder: waiting for the next chat line from the local user
    private String pendingResponder = null;
    private boolean waitingForLocalUser = false;

    // Price data
    private static final Map<String, PriceEntry> PRICE_SBM = new LinkedHashMap<String, PriceEntry>();
    private static final Map<String, PriceEntry> PRICE_SBZ = new LinkedHashMap<String, PriceEntry>();

    // Keybind for opening the mod GUI
    private KeyBinding keyOpenGui;

    // Carry mode & autoCarryMode
    private boolean carryMode = false;
    private boolean autoCarryMode = false;

    // Auto-remove semicolons feature
    private boolean autoRemoveSemicolons = false;

    // GUI notification (displayed in the mod GUI) and timer (in ticks; ~40 ticks = 2 seconds)
    public String guiNotification = "";
    private int guiNotificationTimer = 0;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[AeiroMod] Loaded version " + VERSION + " in " + NAME + "!");
        loadConfig();
        MinecraftForge.EVENT_BUS.register(this);
        initializePrices();

        keyOpenGui = new KeyBinding("Open AeiroMod GUI", Keyboard.KEY_R, "AeiroMod");
        ClientRegistry.registerKeyBinding(keyOpenGui);
    }

    // --------------------------------------------------------
    // GUI Opening – force-close any active screen and open the mod GUI
    // --------------------------------------------------------
    public void openGui() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(null); // Force-close any active screen (chat, etc.)
        mc.displayGuiScreen(new GuiResponders(this));
    }

    // --------------------------------------------------------
    // Keybind Event – if the key is pressed, open the mod GUI
    // --------------------------------------------------------
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent ev) {
        if (keyOpenGui.isPressed()) {
            openGui();
        }
    }

    // --------------------------------------------------------
    // Replace the default chat screen with our custom one (MyGuiChat)
    // --------------------------------------------------------
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.gui instanceof GuiChat && !(e.gui instanceof MyGuiChat)) {
            // Retrieve the default text from the original GuiChat (this will be "/" if slash was pressed)
            GuiChat oldChat = (GuiChat) e.gui;
            String defaultText = "";
            try {
                // Cast the field to GuiTextField, then get its text
                GuiTextField chatInput = (GuiTextField) guiChatInputField.get(oldChat);
                defaultText = chatInput.getText();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            Minecraft.getMinecraft().displayGuiScreen(new MyGuiChat(defaultText));
        }
    }

    // --------------------------------------------------------
    // Custom Chat Screen Subclass – intercept outgoing "?" commands
    // and also process auto-remove semicolons for non-command messages.
    // --------------------------------------------------------
    @SideOnly(Side.CLIENT)
    public class MyGuiChat extends GuiChat {
        // Default constructor calls the parameterized constructor with an empty string
        public MyGuiChat() {
            this("");
        }

        // Constructor that accepts the default chat text
        public MyGuiChat(String defaultText) {
            super(defaultText);
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == Keyboard.KEY_RETURN) {
                String message = this.inputField.getText().trim();
                if (message.startsWith("?")) {
                    Minecraft.getMinecraft().thePlayer.sendChatMessage(message);
                    processOutgoingCommand(message);
                    this.mc.displayGuiScreen(null);
                    return;
                } else {
                    if (autoRemoveSemicolons) {
                        message = modifyOutgoingMessage(message);
                        this.inputField.setText(message);
                    }
                    super.keyTyped(typedChar, keyCode);
                    return;
                }
            }
            super.keyTyped(typedChar, keyCode);
        }
    }

    // --------------------------------------------------------
    // Process Outgoing Command – scan entire message and call the appropriate handler
    // --------------------------------------------------------
    private void processOutgoingCommand(String rawMessage) {
        String lower = rawMessage.toLowerCase();
        if (lower.contains("?carrymode") || lower.contains("?cm")) {
            handleCarryModeCommand();
        }
        else if (lower.contains("?carry") || lower.contains("?c ")) {
            handleCarryCommand(rawMessage);
        }
        else if (lower.contains("?addcarry") || lower.contains("?ac")) {
            handleAddCarryCommand(rawMessage);
        }
        else if (lower.contains("?delcarry") || lower.contains("?dc")) {
            handleDelCarryCommand(rawMessage);
        }
        else if (lower.contains("?finished") || lower.contains("?f")) {
            handleFinishedCommand();
        }
        else if (lower.contains("?list") || lower.contains("?l")) { // Removed trailing space check.
            handleListCommand();
        }
        else if (lower.contains("?help") || lower.contains("?h")) {
            handleHelpCommand();
        }
        else if (lower.contains("?bio")) {
            handleBioCommand();
        }
        else if (lower.contains("?setbio")) {
            handleSetBioCommand(rawMessage);
        }
        else if (lower.contains("?setresponder") || lower.contains("?settrigger")) {
            handleSetResponderCommand(rawMessage);
        }
        else if (lower.contains("?listresponders") || lower.contains("?listtriggers") || lower.contains("?lr")) {
            handleListRespondersCommand();
        }
        else if (lower.contains("?delresponder") || lower.contains("?deltrigger") || lower.contains("?dr")) {
            handleDelResponderCommand(rawMessage);
        }
        else if (lower.contains("?updresponder") || lower.contains("?updtrigger") || lower.contains("?ur")) {
            handleUpdResponderCommand(rawMessage);
        }
        else if (lower.contains("?price")) {
            handlePriceCommand(rawMessage);
        }
        else {
            checkResponders(rawMessage);
        }
    }

    // --------------------------------------------------------
    // Chat Processing via Echo (for responder setup)
    // --------------------------------------------------------
    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent evt) {
        String rawMessage = evt.message.getUnformattedText();
        String lower = rawMessage.toLowerCase();
        if (lower.contains("aeiromod > ")) {
            return;
        }
        if (pendingResponder != null && waitingForLocalUser) {
            String localName = Minecraft.getMinecraft().thePlayer.getName();
            if (rawMessage.contains(localName + ": ")) {
                int idx = rawMessage.indexOf(": ");
                if (idx >= 0) {
                    String resp = rawMessage.substring(idx + 2).trim();
                    ResponderData rd = new ResponderData(resp, 1);
                    customResponders.put(pendingResponder.toLowerCase(), rd);
                    queueChat("Responder set: [" + pendingResponder + "] => [" + resp + "] (enabled)");
                    pendingResponder = null;
                    waitingForLocalUser = false;
                    saveConfig();
                }
            }
            return;
        }
        if (!rawMessage.startsWith("?")) {
            checkResponders(rawMessage);
        }
    }

    // --------------------------------------------------------
    // Client Tick – process queued mod messages and update notification timer
    // --------------------------------------------------------
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase == TickEvent.Phase.END) {
            // Process each queued message: decrement delay, send if delay has expired.
            Iterator<QueuedChatMessage> it = chatQueue.iterator();
            while (it.hasNext()) {
                QueuedChatMessage qcm = it.next();
                qcm.delay--;
                if (qcm.delay < 0) {
                    Minecraft.getMinecraft().thePlayer.sendChatMessage("AeiroMod > " + qcm.message);
                    it.remove();
                }
            }
            if (guiNotificationTimer > 0) {
                guiNotificationTimer--;
                if (guiNotificationTimer <= 0) {
                    guiNotification = "";
                }
            }
        }
    }

    // --------------------------------------------------------
    // Command Handler Methods (Old-Style, scanning raw message)
    // --------------------------------------------------------
    private void handleCarryModeCommand() {
        setCarryMode(!carryMode, true);
    }
    public void setCarryMode(boolean b, boolean sendMessage) {
        carryMode = b;
        saveConfig();
        if (sendMessage) {
            queueChat("carryMode is now " + (carryMode ? "ON" : "OFF"));
        }
    }
    private void handleCarryCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?carry", "?c");
        if (idx == -1) return;
        if (idx + 3 >= parts.length) {
            queueChat("Usage: ?carry <user> <diff> <amount>");
            return;
        }
        String user = parts[idx + 1];
        String diff = parts[idx + 2].toLowerCase();
        int amount = parseIntSafe(parts[idx + 3], -1);
        if (!isValidDifficulty(diff) || amount < 1) {
            queueChat("Invalid usage! e.g. ?carry Bob m5 10");
            return;
        }
        clients.add(user);
        runDifficulty.add(diff);
        runsCompleted.add(0);
        runsRequested.add(amount);
        queueChat("Initialized carry for " + user + " [" + diff + "] x " + amount);
        if (autoCarryMode && !carryMode) {
            setCarryMode(true, false);
        }
        saveConfig();
    }
    private void handleAddCarryCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?addcarry", "?ac");
        if (idx == -1) return;
        if (idx + 3 >= parts.length) {
            queueChat("Usage: ?addcarry <user> <diff> <amt>");
            return;
        }
        String user = parts[idx + 1];
        String diff = parts[idx + 2].toLowerCase();
        int amt = parseIntSafe(parts[idx + 3], -1);
        if (!isValidDifficulty(diff) || amt < 1) {
            queueChat("Invalid usage! e.g. ?ac Bob m5 10");
            return;
        }
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).equalsIgnoreCase(user) && runDifficulty.get(i).equalsIgnoreCase(diff)) {
                int oldReq = runsRequested.get(i);
                int newReq = oldReq + amt;
                runsRequested.set(i, newReq);
                queueChat("Updated carry: " + user + " [" + diff + "], now x " + newReq);
                if (autoCarryMode && !carryMode) {
                    setCarryMode(true, false);
                }
                saveConfig();
                return;
            }
        }
        clients.add(user);
        runDifficulty.add(diff);
        runsCompleted.add(0);
        runsRequested.add(amt);
        queueChat("Added carry: " + user + " [" + diff + "] x " + amt);
        if (autoCarryMode && !carryMode) {
            setCarryMode(true, false);
        }
        saveConfig();
    }
    private void handleDelCarryCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?delcarry", "?dc");
        if (idx == -1) return;
        if (idx + 1 >= parts.length) {
            queueChat("Usage: ?delcarry <index>");
            return;
        }
        int carryIndex = parseIntSafe(parts[idx + 1], -1);
        if (carryIndex < 1 || carryIndex > clients.size()) {
            queueChat("Invalid index. We have only " + clients.size() + " carries.");
            return;
        }
        int realIndex = carryIndex - 1;
        String user = clients.remove(realIndex);
        String diff = runDifficulty.remove(realIndex);
        int req = runsRequested.remove(realIndex);
        int done = runsCompleted.remove(realIndex);
        queueChat("Deleted carry #" + carryIndex + ": " + user + " [" + diff + "] " + done + "/" + req);
        if (autoCarryMode && clients.isEmpty() && carryMode) {
            setCarryMode(false, false);
        }
        saveConfig();
    }
    private void handleFinishedCommand() {
        if (clients.isEmpty()){
            queueChat("No carries to finish.");
            return;
        }
        for (int i = 0; i < clients.size(); i++){
            runsCompleted.set(i, runsCompleted.get(i) + 1);
        }
        List<Integer> toRemove = new ArrayList<Integer>();
        for (int i = 0; i < clients.size(); i++){
            int remain = runsRequested.get(i) - runsCompleted.get(i);
            String user = clients.get(i);
            String diff = runDifficulty.get(i);
            if (remain <= 0){
                queueChat("Carry completed: " + user + " [" + diff + "]");
                toRemove.add(i);
            } else {
                queueChat("Carry logged for " + user + " [" + diff + "]: " + remain + " remain");
            }
        }
        for (int i = toRemove.size() - 1; i >= 0; i--){
            int index = toRemove.get(i);
            clients.remove(index);
            runDifficulty.remove(index);
            runsRequested.remove(index);
            runsCompleted.remove(index);
        }
        if (autoCarryMode && clients.isEmpty() && carryMode) {
            setCarryMode(false, false);
        }
        saveConfig();
    }
    private void handleListCommand() {
        if (clients.isEmpty()){
            queueChat("No active carries.");
            return;
        }
        for (int i = 0; i < clients.size(); i++){
            String user = clients.get(i);
            String diff = runDifficulty.get(i);
            int done = runsCompleted.get(i);
            int req = runsRequested.get(i);
            queueChat((i + 1) + ") " + user + " [" + diff + "]: " + done + "/" + req + " done (" + (req - done) + " remain)");
        }
    }
    private void handleHelpCommand() {
        String allCommands =
                "?carry(?c), ?addcarry(?ac), ?delcarry(?dc), ?finished(?f), ?list(?l), " +
                        "?bio, ?setbio <txt>, ?price <m1..m7> <sbm|sbz> [amt], " +
                        "?carrymode(?cm), ?help(?h)";
        queueChat("Commands: " + allCommands);
    }
    private void handleBioCommand() {
        if (userBio == null || userBio.trim().isEmpty()){
            queueChat("No bio set. Use ?setbio <your bio> to set one!");
        } else {
            queueChat("Your bio: " + userBio);
        }
    }
    private void handleSetBioCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?setbio");
        if (idx == -1) return;
        if (idx + 1 >= parts.length) {
            queueChat("Usage: ?setbio <text>");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx + 1; i < parts.length; i++){
            sb.append(parts[i]).append(" ");
        }
        String newBio = sb.toString().trim();
        if (newBio.isEmpty()){
            queueChat("Usage: ?setbio <text>");
            return;
        }
        userBio = newBio;
        saveConfig();
        queueChat("Bio set to: " + newBio);
    }
    private void handleSetResponderCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?setresponder", "?settrigger");
        if (idx == -1) return;
        if (idx + 1 >= parts.length) {
            queueChat("Usage: ?setresponder <phrase>");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx + 1; i < parts.length; i++){
            sb.append(parts[i]).append(" ");
        }
        String phrase = sb.toString().trim().toLowerCase();
        if (phrase.isEmpty()){
            queueChat("Usage: ?setresponder <phrase>");
            return;
        }
        pendingResponder = phrase;
        waitingForLocalUser = true;
        queueChat("Now type the next line (as YOU) for: " + phrase + " (we'll store text after ': ').");
    }
    private void handleListRespondersCommand() {
        if (customResponders.isEmpty()){
            queueChat("No auto-responders found.");
            return;
        }
        List<Map.Entry<String, ResponderData>> list = new ArrayList<Map.Entry<String, ResponderData>>(customResponders.entrySet());
        for (int i = 0; i < list.size(); i++){
            Map.Entry<String, ResponderData> e = list.get(i);
            String phrase = e.getKey();
            ResponderData rd = e.getValue();
            String modeStr = (rd.mode == 0 ? "DIS" : (rd.mode == 1 ? "EN" : "CAR"));
            queueChat((i + 1) + ") " + phrase + " => " + rd.response + " [" + modeStr + "]");
        }
    }
    private void handleDelResponderCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?delresponder", "?dr", "?deltrigger");
        if (idx == -1) return;
        if (idx + 1 >= parts.length) {
            queueChat("Usage: ?delresponder <index>");
            return;
        }
        int delIndex = parseIntSafe(parts[idx + 1], -1);
        if (delIndex < 1) {
            queueChat("Invalid index. e.g. ?dr 1");
            return;
        }
        List<Map.Entry<String, ResponderData>> list = new ArrayList<Map.Entry<String, ResponderData>>(customResponders.entrySet());
        if (delIndex > list.size()) {
            queueChat("Index out of range. We have only " + list.size() + " responders.");
            return;
        }
        int real = delIndex - 1;
        Map.Entry<String, ResponderData> ent = list.get(real);
        customResponders.remove(ent.getKey());
        saveConfig();
        queueChat("Deleted responder #" + delIndex + " (" + ent.getKey() + ")");
    }
    private void handleUpdResponderCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?updresponder", "?ur", "?updtrigger");
        if (idx == -1) return;
        if (idx + 2 >= parts.length) {
            queueChat("Usage: ?updresponder <index> <new response>");
            return;
        }
        int updIndex = parseIntSafe(parts[idx + 1], -1);
        if (updIndex < 1 || updIndex > customResponders.size()) {
            queueChat("Invalid index. Only " + customResponders.size() + " responders exist.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx + 2; i < parts.length; i++){
            sb.append(parts[i]).append(" ");
        }
        String newResponse = sb.toString().trim();
        if (newResponse.isEmpty()) {
            queueChat("Usage: ?updresponder <index> <new response>");
            return;
        }
        List<Map.Entry<String, ResponderData>> list = new ArrayList<Map.Entry<String, ResponderData>>(customResponders.entrySet());
        int real = updIndex - 1;
        Map.Entry<String, ResponderData> e = list.get(real);
        String phrase = e.getKey();
        ResponderData rd = e.getValue();
        rd.response = newResponse;
        customResponders.put(phrase, rd);
        saveConfig();
        queueChat("Updated responder #" + updIndex + " (" + phrase + ") => " + newResponse);
    }
    private void handlePriceCommand(String rawMessage) {
        String[] parts = rawMessage.split("\\s+");
        int idx = findCommandIndex(parts, "?price");
        if (idx == -1) return;
        if (idx + 2 >= parts.length) {
            queueChat("Usage: ?price <m1..m7> <sbm/sbz> [amount]");
            return;
        }
        String diff = parts[idx + 1].toLowerCase();
        String server = parts[idx + 2].toLowerCase();
        int amount = -1;
        if (idx + 3 < parts.length) {
            amount = parseIntSafe(parts[idx + 3], -1);
        }
        if (!diff.matches("m[1-7]")) {
            queueChat("Only supporting m1..m7 for now. e.g. ?price m3 sbm [amount]");
            return;
        }
        if (!(server.equals("sbm") || server.equals("sbz"))) {
            queueChat("Unknown server: " + server + ". Valid: sbm / sbz");
            return;
        }
        PriceEntry pe = (server.equals("sbm")) ? PRICE_SBM.get(diff) : PRICE_SBZ.get(diff);
        if (pe == null) {
            queueChat("No price data for " + server + " " + diff);
            return;
        }
        float singleVal = parsePriceString(pe.single);
        float bulkVal  = parsePriceString(pe.bulk);
        if (amount < 1) {
            if (bulkVal > 0) {
                queueChat(diff + "@" + server + ": Single=" + pe.single + ", Bulk(5)=" + pe.bulk);
            } else {
                queueChat(diff + "@" + server + ": Single=" + pe.single + " (No bulk price)");
            }
            return;
        }
        float total;
        if (amount >= 5 && bulkVal > 0) {
            total = bulkVal * amount;
        } else {
            total = singleVal * amount;
        }
        String costStr = formatCost(total) + "m";
        queueChat("For " + amount + "x " + diff + " on " + server + ": " + costStr);
    }

    // --------------------------------------------------------
    // Check Responders (for auto-response triggers)
    // --------------------------------------------------------
    private void checkResponders(String rawMessage) {
        if (rawMessage.startsWith("/")) {
            return;
        }
        String lower = rawMessage.toLowerCase();
        Iterator<Map.Entry<String, ResponderData>> it = customResponders.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<String, ResponderData> e = it.next();
            String phrase = e.getKey().toLowerCase();
            ResponderData rd = e.getValue();
            if (rd.mode == 0) continue;
            if (rd.mode == 2 && !carryMode) continue;
            if (!phrase.isEmpty() && lower.contains(phrase)){
                queueChat(rd.response);
                break;
            }
        }
    }

    // --------------------------------------------------------
    // Price Data Inner Class
    // --------------------------------------------------------
    private static class PriceEntry {
        public String single;
        public String bulk;
        public PriceEntry(String s, String b) { single = s; bulk = b; }
    }

    private void initializePrices() {
        PRICE_SBM.put("m1", new PriceEntry("1.25m", "1m"));
        PRICE_SBM.put("m2", new PriceEntry("2.2m", "2.2m"));
        PRICE_SBM.put("m3", new PriceEntry("4m", "3.6m"));
        PRICE_SBM.put("m4", new PriceEntry("15m", "0"));
        PRICE_SBM.put("m5", new PriceEntry("5.75m", "5.25m"));
        PRICE_SBM.put("m6", new PriceEntry("8m", "6.75m"));
        PRICE_SBM.put("m7", new PriceEntry("35m", "30m"));

        PRICE_SBZ.put("m1", new PriceEntry("1.2m", "1m"));
        PRICE_SBZ.put("m2", new PriceEntry("2.3m", "2.1m"));
        PRICE_SBZ.put("m3", new PriceEntry("3.5m", "3.3m"));
        PRICE_SBZ.put("m4", new PriceEntry("14m", "0"));
        PRICE_SBZ.put("m5", new PriceEntry("5.6m", "5.2m"));
        PRICE_SBZ.put("m6", new PriceEntry("7.5m", "7m"));
        PRICE_SBZ.put("m7", new PriceEntry("32m", "28m"));
    }

    // --------------------------------------------------------
    // Utility Methods
    // --------------------------------------------------------
    private float parsePriceString(String s) {
        if (s == null) return 0f;
        if (s.equals("0")) return 0f;
        String tmp = s.toLowerCase();
        if (tmp.endsWith("m")){
            tmp = tmp.substring(0, tmp.length() - 1).trim();
        }
        try {
            return Float.parseFloat(tmp);
        } catch(NumberFormatException e) {
            return 0f;
        }
    }
    private String formatCost(float val) {
        if (val == (long)val) {
            return String.valueOf((long) val);
        } else {
            return String.format("%.2f", val);
        }
    }
    private boolean isValidDifficulty(String d) {
        return d.matches("(m|f)[1-7]") || d.matches("[ebz][2-5]");
    }
    private int parseIntSafe(String s, int defVal) {
        try {
            return Integer.parseInt(s);
        } catch(NumberFormatException e) {
            return defVal;
        }
    }
    private int findCommandIndex(String[] parts, String... commands) {
        for (int i = 0; i < parts.length; i++){
            String p = parts[i].toLowerCase();
            for (String cmd : commands) {
                if (p.equals(cmd)) {
                    return i;
                }
            }
        }
        return -1;
    }

    // --------------------------------------------------------
    // Chat & Configuration Management
    // --------------------------------------------------------
    public void queueChat(String msg) {
        // Queue the message with a delay of 1 tick
        chatQueue.add(new QueuedChatMessage(msg, 1));
    }

    public void loadConfig() {
        File cfg = new File(Minecraft.getMinecraft().mcDataDir, "config/" + CONFIG_FILE_NAME);
        if (!cfg.exists()) return;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(cfg));
            String line;
            while((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("bio=")) {
                    userBio = line.substring("bio=".length()).trim();
                }
                else if (line.startsWith("carryMode=")) {
                    carryMode = "true".equalsIgnoreCase(line.substring("carryMode=".length()).trim());
                }
                else if (line.startsWith("autoCarryMode=")) {
                    autoCarryMode = "true".equalsIgnoreCase(line.substring("autoCarryMode=".length()).trim());
                }
                // --- New: load autoRemoveSemicolons ---
                else if (line.startsWith("autoRemoveSemicolons=")) {
                    autoRemoveSemicolons = "true".equalsIgnoreCase(line.substring("autoRemoveSemicolons=".length()).trim());
                }
                else if (line.startsWith("responder=")) {
                    String data = line.substring("responder=".length());
                    String[] sub = data.split("\\|\\|");
                    if (sub.length >= 3) {
                        String phrase = sub[0].trim().toLowerCase();
                        String resp = sub[1].trim();
                        int mode = parseIntSafe(sub[2].trim(), 1);
                        customResponders.put(phrase, new ResponderData(resp, mode));
                    }
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) try { br.close(); } catch(IOException ex){}
        }
    }

    public void saveConfig() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        if (!dir.exists()) dir.mkdirs();
        File cfg = new File(dir, CONFIG_FILE_NAME);
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(cfg));
            pw.println("bio=" + userBio);
            pw.println("carryMode=" + (carryMode ? "true" : "false"));
            pw.println("autoCarryMode=" + (autoCarryMode ? "true" : "false"));
            // --- New: save autoRemoveSemicolons ---
            pw.println("autoRemoveSemicolons=" + (autoRemoveSemicolons ? "true" : "false"));
            Iterator<Map.Entry<String, ResponderData>> it = customResponders.entrySet().iterator();
            while(it.hasNext()){
                Map.Entry<String, ResponderData> e = it.next();
                ResponderData rd = e.getValue();
                pw.println("responder=" + e.getKey() + "||" + rd.response + "||" + rd.mode);
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if (pw != null) pw.close();
        }
    }

    // --------------------------------------------------------
    // Getters/Setters for GUI and Responders
    // --------------------------------------------------------
    public LinkedHashMap<String, ResponderData> getCustomResponders() {
        return customResponders;
    }
    public String getUserBio() { return userBio; }
    public void setUserBio(String s) {
        userBio = s;
        saveConfig();
    }
    public boolean isCarryMode() { return carryMode; }
    public void setCarryMode(boolean b) { setCarryMode(b, true); }
    public boolean isAutoCarryMode() { return autoCarryMode; }
    // Overload with two parameters
    public void setAutoCarryMode(boolean b, boolean notify) {
        autoCarryMode = b;
        saveConfig();
        if (notify) {
            setGuiNotification("autoCarryMode is now " + (autoCarryMode ? "ON" : "OFF"));
        }
    }
    public boolean isAutoRemoveSemicolons() {
        return autoRemoveSemicolons;
    }
    public void setAutoRemoveSemicolons(boolean enabled) {
        this.autoRemoveSemicolons = enabled;
        saveConfig();
        setGuiNotification("Auto-remove semicolons is now " + (autoRemoveSemicolons ? "ON" : "OFF"));
    }
    public String modifyOutgoingMessage(String message) {
        if (autoRemoveSemicolons) {
            return message.replace(";", "");
        }
        return message;
    }
    // --------------------------------------------------------
    // Responder Data Inner Class
    // --------------------------------------------------------
    public class ResponderData {
        public String response;
        public int mode;
        public ResponderData(String r, int m) {
            response = r;
            mode = m;
        }
    }
    // --------------------------------------------------------
    // Additional Methods for GuiResponders
    // --------------------------------------------------------
    public void setResponder(String phrase, String resp) {
        ResponderData rd = customResponders.get(phrase.toLowerCase());
        if (rd != null) {
            rd.response = resp;
        } else {
            rd = new ResponderData(resp, 1);
            customResponders.put(phrase.toLowerCase(), rd);
        }
        saveConfig();
    }
    public void removeResponder(String phrase) {
        customResponders.remove(phrase.toLowerCase());
        saveConfig();
    }
    public void setGuiNotification(String msg) {
        this.guiNotification = msg;
        this.guiNotificationTimer = 40;
    }
}