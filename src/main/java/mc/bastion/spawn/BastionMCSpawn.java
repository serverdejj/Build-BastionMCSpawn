package mc.bastion.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class BastionMCSpawn extends JavaPlugin implements Listener, CommandExecutor {
    private File spawnFile;
    private YamlConfiguration spawnConfig;

    @Override public void onEnable() {
        saveDefaultConfig();
        spawnFile = new File(getDataFolder(), "spawn.yml");
        spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
        Objects.requireNonNull(getCommand("spawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("setspawn")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + body);
    }

    private Location getSpawn() {
        if (!spawnConfig.contains("spawn.world")) return null;
        World world = Bukkit.getWorld(spawnConfig.getString("spawn.world", ""));
        if (world == null) return null;
        return new Location(world, spawnConfig.getDouble("spawn.x"), spawnConfig.getDouble("spawn.y"),
                spawnConfig.getDouble("spawn.z"), (float) spawnConfig.getDouble("spawn.yaw"),
                (float) spawnConfig.getDouble("spawn.pitch"));
    }

    private void saveSpawn(Location location) throws IOException {
        spawnConfig.set("spawn.world", location.getWorld().getName());
        spawnConfig.set("spawn.x", location.getX());
        spawnConfig.set("spawn.y", location.getY());
        spawnConfig.set("spawn.z", location.getZ());
        spawnConfig.set("spawn.yaw", location.getYaw());
        spawnConfig.set("spawn.pitch", location.getPitch());
        spawnConfig.save(spawnFile);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return true; }
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("bastionmcspawn.admin")) { player.sendMessage(msg("no-permission")); return true; }
            try {
                saveSpawn(player.getLocation());
                // Also sets the vanilla world spawn near the selected location.
                player.getWorld().setSpawnLocation(player.getLocation());
                player.sendMessage(msg("spawn-set"));
            } catch (IOException e) {
                getLogger().severe("No se pudo guardar spawn.yml: " + e.getMessage());
                player.sendMessage(ChatColor.RED + "Error al guardar el spawn. Revisa la consola.");
            }
            return true;
        }
        if (!player.hasPermission("bastionmcspawn.spawn")) { player.sendMessage(msg("no-permission")); return true; }
        Location destination = getSpawn();
        if (destination == null) { player.sendMessage(msg(spawnConfig.contains("spawn.world") ? "world-missing" : "spawn-missing")); return true; }
        player.teleportAsync(destination).thenAccept(success -> {
            if (success) Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.sendMessage(msg("spawn-teleport"));
            });
        }).exceptionally(error -> { getLogger().warning("Error en /spawn: " + error); return null; });
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH) public void onFirstJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) return;
        Location destination = getSpawn();
        if (destination == null) return;
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) player.teleportAsync(destination);
        });
    }

    @EventHandler(priority = EventPriority.HIGH) public void onRespawn(PlayerRespawnEvent event) {
        Location destination = getSpawn();
        if (destination != null) event.setRespawnLocation(destination);
    }
}
