package com.am.aeiromod;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * AeiroModCommand handles the chat slash commands for AeiroMod.
 * Commands:
 * - /aeiromod or /am: Opens the AeiroMod GUI.
 */
public class AeiroModCommand extends CommandBase {

    private final AeiroMod mod;

    public AeiroModCommand(AeiroMod mod) {
        this.mod = mod;
    }

    @Override
    public String getCommandName() {
        return "aeiromod";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/aeiromod or /am - Opens the AeiroMod GUI";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // Instead of calling mod.openGui() (which the compiler cannot find),
        // we directly display the GUI by constructing a new GuiResponders.
        Minecraft.getMinecraft().displayGuiScreen(new GuiResponders(mod));
        System.out.println("[AeiroModCommand] GUI opened via displayGuiScreen(new GuiResponders(mod))");
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("am");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Allow all users to execute this command.
    }
}