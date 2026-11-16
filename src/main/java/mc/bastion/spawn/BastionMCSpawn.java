package mc.bastion.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BastionMCSpawn extends JavaPlugin implements Listener, CommandExecutor {
    private File spawnFile, messagesFile;
    private YamlConfiguration spawnConfig, messagesConfig;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Location> startingPositions = new HashMap<>();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        spawnFile = new File(getDataFolder(), "spawn.yml");
        messagesFile = new File(getDataFolder(), "messages.yml");
        spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        Objects.requireNonNull(getCommand("spawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("setspawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("bastionspawn")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() {
        for (BukkitTask task : pending.values()) task.cancel();
        pending.clear(); startingPositions.clear();
    }

    // Accepts &-codes, &#RRGGBB, MiniMessage <gradient:...> and <#RRGGBB>.
    private Component format(String raw) {
        if (raw == null) raw = "";
        String hex = HEX.matcher(raw).replaceAll("<#$1>");
        Matcher matcher = LEGACY.matcher(hex);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>"; case '1' -> "<dark_blue>"; case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>"; case '4' -> "<dark_red>"; case '5' -> "<dark_purple>";
                case '6' -> "<gold>"; case '7' -> "<gray>"; case '8' -> "<dark_gray>";
                case '9' -> "<blue>"; case 'a' -> "<green>"; case 'b' -> "<aqua>";
                case 'c' -> "<red>"; case 'd' -> "<light_purple>"; case 'e' -> "<yellow>";
                case 'f' -> "<white>"; case 'l' -> "<bold>"; case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>"; case 'o' -> "<italic>"; case 'r' -> "<reset>";
                default -> "";
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(out);
        return MINI.deserialize(out.toString()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private void send(CommandSender target, String key, String... replacements) {
        String prefix = messagesConfig.getString("messages.prefix", "");
        String message = messagesConfig.getString("messages." + key, "");
        message = message.replace("{prefix}", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            message = message.replace(replacements[i], replacements[i + 1]);
        target.sendMessage(format(message));
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
        spawnConfig.set("spawn.x", location.getX()); spawnConfig.set("spawn.y", location.getY());
        spawnConfig.set("spawn.z", location.getZ()); spawnConfig.set("spawn.yaw", location.getYaw());
        spawnConfig.set("spawn.pitch", location.getPitch()); spawnConfig.save(spawnFile);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bastionspawn")) {
            if (!sender.hasPermission("bastionmcspawn.admin")) { send(sender, "no-permission"); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
                spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
                send(sender, "reloaded");
            } else send(sender, "reload-usage");
            return true;
        }
        if (!(sender instanceof Player player)) { send(sender, "player-only"); return true; }
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("bastionmcspawn.admin")) { send(player, "no-permission"); return true; }
            try {
                saveSpawn(player.getLocation());
                player.getWorld().setSpawnLocation(player.getLocation());
                send(player, "spawn-set");
            } catch (IOException e) {
                getLogger().severe("No se pudo guardar spawn.yml: " + e.getMessage());
                send(player, "save-error");
            }
            return true;
        }
        if (!player.hasPermission("bastionmcspawn.spawn")) { send(player, "no-permission"); return true; }
        Location destination = getSpawn();
        if (destination == null) { send(player, spawnConfig.contains("spawn.world") ? "world-missing" : "spawn-missing"); return true; }
        if (pending.containsKey(player.getUniqueId())) { send(player, "already-waiting"); return true; }
        int seconds = Math.max(0, getConfig().getInt("teleport.delay-seconds", 5));
        if (getConfig().getBoolean("teleport.op-bypass", false) && player.isOp()) seconds = 0;
        if (seconds == 0) { teleport(player, destination); return true; }
        UUID id = player.getUniqueId();
        startingPositions.put(id, player.getLocation().clone());
        final int[] remaining = {seconds};
        send(player, "countdown", "{seconds}", Integer.toString(remaining[0]));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || player.isDead()) { cancel(id, false); return; }
            remaining[0]--;
            if (remaining[0] <= 0) {
                cancel(id, false);
                teleport(player, destination);
            } else send(player, "countdown", "{seconds}", Integer.toString(remaining[0]));
        }, 20L, 20L);
        pending.put(id, task);
        return true;
    }

    private void teleport(Player player, Location destination) {
        player.teleportAsync(destination).whenComplete((success, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null || !Boolean.TRUE.equals(success)) {
                    getLogger().warning("Fallo en /spawn para " + player.getName() + ": " + error);
                    if (player.isOnline()) send(player, "teleport-failed");
                } else if (player.isOnline()) send(player, "spawn-teleport");
            }));
    }

    private void cancel(UUID id, boolean notify) {
        BukkitTask task = pending.remove(id);
        if (task != null) task.cancel();
        startingPositions.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (notify && player != null && player.isOnline()) send(player, "cancelled-movement");
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!pending.containsKey(id) || !getConfig().getBoolean("teleport.cancel-on-move", true)) return;
        Location from = startingPositions.get(id), to = event.getTo();
        if (from == null || to == null || !from.getWorld().equals(to.getWorld()) ||
                from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())
            cancel(id, true);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId(), false); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity().getUniqueId(), false); }

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
