package mc.jazhdo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BridgeListener implements Listener {
    private final BridgeGame game;
    private final BridgePlugin plugin;

    public BridgeListener(BridgeGame game, BridgePlugin plugin) {
        this.game = game;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        game.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        // Check world
        Location loc = event.getTo();
        if (!loc.getWorld().getName().equals("bridge")) return;

        // Simulate void
        Player player = event.getPlayer();
        if (event.getTo().getY() < plugin.getConfig().getInt("void-y")) {
            player.setHealth(0);
            return;
        }

        // Check y-level to make sure its below the portal
        if (loc.getBlockY() <= plugin.getConfig().getInt("portal-y")) {
            // Check touching portal
            Block feet = loc.getBlock();
            Block below = loc.clone().subtract(0, 1, 0).getBlock();
            if (feet.getType() == Material.ENDER_PORTAL || below.getType() == Material.ENDER_PORTAL) game.handlePortalTouch(player, loc);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.getWorld().getName().equals("bridge")) return;

        event.setDeathMessage(null);
        event.getDrops().clear();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game.getState() == BridgeGame.State.RUNNING) {
                player.spigot().respawn();
                game.respawnPlayer(player);
            } else player.teleport(Bukkit.getWorld("bridge").getSpawnLocation());
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Make sure player is joining the Bridge world
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("bridge")) return;

        // Check if a game is currently in progress
        BridgeGame.State state = game.getState();
        if (state == BridgeGame.State.RUNNING) player.sendMessage(ChatColor.RED + "A game is already in progress!");
        else {
            // Reset countdown if someone joins or just teleport the player
            if (state == BridgeGame.State.COUNTDOWN) game.countdownTimer = 10;
            player.teleport(Bukkit.getWorld(plugin.getConfig().getString("lobby")).getSpawnLocation());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (game.getState() != BridgeGame.State.RUNNING) event.setCancelled(true);
        Player victim = (Player) event.getEntity();
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getDamager();
            if (arrow.getShooter() instanceof Player) attacker = (Player) arrow.getShooter();
        }

        if (attacker == null) return;

        if (game.getTeam(victim).equals(game.getTeam(attacker))) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getPlayer().getWorld().getName().equals("bridge")) return;
        if (event.getBlock().getLocation().getY() > 100) event.setCancelled(true);
        if (Math.abs(event.getBlock().getLocation().getZ()) > 20) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().getWorld().getName().equals("bridge")) return;
        if (event.getBlock().getY() > 100) event.setCancelled(true);
        else if (Math.abs(event.getBlock().getZ()) > 20) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot build in this area!");
        } else if (Math.abs(event.getBlock().getX()) > 4) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot build this far out!");
        }
    }
}