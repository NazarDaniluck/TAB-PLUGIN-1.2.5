package main.java.com.tabplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class TabManager {
    private final JavaPlugin plugin;
    private List<String> tabLines;
    private List<String> displayNames;
    private boolean sortPrefix;

    // Reflection fields
    private Class<?> packetClass;
    private Constructor<?> packetConstructor;
    private Method getHandleMethod;
    private Field connectionField;
    private Method sendPacketMethod;
    private boolean reflectionOk = false;

    public TabManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupReflection();
    }

    private void setupReflection() {
        try {
            // CraftBukkit classes
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            getHandleMethod = craftPlayerClass.getMethod("getHandle");

            // NMS classes
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server.EntityPlayer");

            try {
                connectionField = entityPlayerClass.getField("netServerHandler");
            } catch (NoSuchFieldException e) {

                connectionField = entityPlayerClass.getField("playerConnection");
            }
            connectionField.setAccessible(true);

            Class<?> connectionClass = connectionField.getType();

            sendPacketMethod = connectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server.Packet"));

            packetClass = Class.forName("net.minecraft.server.Packet201PlayerInfo");

            try {
                packetConstructor = packetClass.getConstructor(String.class, boolean.class, int.class);
            } catch (NoSuchMethodException e) {
                try {
                    packetConstructor = packetClass.getConstructor(String.class, boolean.class, short.class);
                } catch (NoSuchMethodException e2) {
                    packetConstructor = null;
                }
            }

            reflectionOk = true;
            plugin.getLogger().info("Reflection setup successful. Using connection field: " + connectionField.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to setup reflection for TabManager. Plugin will not work.", e);
        }
    }

    public void loadConfig() {
        if (!reflectionOk) return;

        sortPrefix = plugin.getConfig().getBoolean("sort-prefix", true);
        tabLines = plugin.getConfig().getStringList("lines");
        displayNames = new ArrayList<>(tabLines.size());

        for (int i = 0; i < tabLines.size(); i++) {
            String line = tabLines.get(i);
            if (line == null || line.isEmpty()) {
                line = " ";
            }

            String colored = ChatColor.translateAlternateColorCodes('&', line);

            if (sortPrefix) {
                char prefix = (char) (0x01 + i);
                colored = prefix + colored;
            }

            if (colored.length() > 16) {
                colored = colored.substring(0, 16);
                plugin.getLogger().warning("Line " + i + " is too long after colors, truncated to 16 chars.");
            }

            displayNames.add(colored);
        }
    }

    public void sendToPlayer(Player player) {
        if (!reflectionOk || displayNames == null || displayNames.isEmpty()) return;

        try {
            Object entityPlayer = getHandleMethod.invoke(player);
            Object connection = connectionField.get(entityPlayer); // получаем NetServerHandler или PlayerConnection

            for (String name : displayNames) {
                Object packet = createPacket(name, true, 0);
                sendPacketMethod.invoke(connection, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send tab entries to " + player.getName(), e);
        }
    }

    public void removeFromPlayer(Player player) {
        if (!reflectionOk || displayNames == null || displayNames.isEmpty()) return;

        try {
            Object entityPlayer = getHandleMethod.invoke(player);
            Object connection = connectionField.get(entityPlayer);

            for (String name : displayNames) {
                Object packet = createPacket(name, false, 0);
                sendPacketMethod.invoke(connection, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove tab entries from " + player.getName(), e);
        }
    }

    public void updateAll() {
        if (!reflectionOk) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeFromPlayer(player);
            sendToPlayer(player);
        }
    }

    public void removeAll() {
        if (!reflectionOk) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeFromPlayer(player);
        }
    }

    private Object createPacket(String playerName, boolean connected, int ping) throws Exception {
        if (packetConstructor != null) {
            try {
                return packetConstructor.newInstance(playerName, connected, ping);
            } catch (Exception e) {
                if (packetConstructor.getParameterTypes()[2] == short.class) {
                    return packetConstructor.newInstance(playerName, connected, (short) ping);
                } else {
                    throw e;
                }
            }
        } else {
            Object packet = packetClass.newInstance();
            Field nameField = null, connectedField = null, pingField = null;
            for (Field f : packetClass.getDeclaredFields()) {
                if (f.getType() == String.class) nameField = f;
                else if (f.getType() == boolean.class) connectedField = f;
                else if (f.getType() == int.class || f.getType() == short.class) pingField = f;
            }
            if (nameField == null || connectedField == null || pingField == null) {
                throw new IllegalStateException("Cannot find required fields in Packet201PlayerInfo");
            }
            nameField.setAccessible(true);
            connectedField.setAccessible(true);
            pingField.setAccessible(true);
            nameField.set(packet, playerName);
            connectedField.set(packet, connected);
            pingField.set(packet, ping);
            return packet;
        }
    }
}