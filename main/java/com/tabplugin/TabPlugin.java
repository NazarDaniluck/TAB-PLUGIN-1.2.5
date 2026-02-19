package main.java.com.tabplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TabPlugin extends JavaPlugin {
    private TabManager tabManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tabManager = new TabManager(this);
        tabManager.loadConfig();

        Bukkit.getPluginManager().registerEvents(new PlayerListener(tabManager), this);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    tabManager.sendToPlayer(p);
                }
            }
        });

        getLogger().info("TabPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (tabManager != null) {
            tabManager.removeAll();
        }
        getLogger().info("TabPlugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tabreload")) {
            if (!sender.hasPermission("tabplugin.reload")) {
                sender.sendMessage("You don't have permission.");
                return true;
            }
            reloadConfig();
            tabManager.loadConfig();
            tabManager.updateAll();
            sender.sendMessage("Tab configuration reloaded.");
            return true;
        }
        return false;
    }
}