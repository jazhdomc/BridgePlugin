package mc.jazhdo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BridgeGame {
    public enum State { WAITING, COUNTDOWN, RUNNING, ENDED }

    private final BridgePlugin plugin;
    private State state = State.WAITING;
    public int countdownTimer = 10;
    private int redScore = 0, blueScore = 0;
    private BukkitRunnable gameLoop;
    private Map<UUID, String> teams = new HashMap<>();
    private org.bukkit.scoreboard.Scoreboard scoreboard;
    private org.bukkit.scoreboard.Objective objective;
    private int gameTimer;

    public BridgeGame(BridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void startLoop() {
        gameLoop = new BukkitRunnable() {
            @Override
            public void run() {
                switch (state) {
                    case WAITING:
                        // Check if players are enough (>= 2)
                        World world = Bukkit.getWorld("bridge");
                        if (world.getPlayers().size() >= 2) {
                            setState(State.COUNTDOWN);
                            countdownTimer = 10;
                        } else
                            for (Player p : world.getPlayers())
                                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ChatColor.YELLOW + "Waiting for players... (" + world.getPlayers().size() + "/2+)"));
                        break;
                    case COUNTDOWN:
                        World bridgeWorld = Bukkit.getWorld("bridge");

                        // Check if players choose to leave during the countdown
                        if (bridgeWorld.getPlayers().size() < 2) {
                            setState(State.WAITING);
                            broadcast(ChatColor.RED + "Not enough players.");
                            return;
                        }

                        if (countdownTimer > 0) {
                            // Update Countdown
                            for (Player p : bridgeWorld.getPlayers()) {
                                p.sendTitle(ChatColor.GREEN + String.valueOf(countdownTimer), ChatColor.GRAY + "Prepare!", 5, 18, 7);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            }
                            countdownTimer--;
                        } else {
                            setState(State.RUNNING);
                            gameTimer = plugin.getConfig().getInt("game-seconds-length");
                        }
                        break;
                    case RUNNING:
                        if (state != State.RUNNING) break;

                        // Check for wins
                        if (redScore != blueScore) {
                            if (redScore >= plugin.getConfig().getInt("winning-score")) endGame("Red");
                            else if (blueScore >= plugin.getConfig().getInt("winning-score")) endGame("Blue");
                        }

                        // Handle game timer
                        if (gameTimer > 0) gameTimer--;
                        else endGame("Stalling");
                        break;
                    case ENDED:
                        break;
                }
            }
        };
        // Run tick every 20 ticks (every second)
        gameLoop.runTaskTimer(plugin, 0L, 20L);
    }

    public void handlePortalTouch(Player player, Location loc) {
        if (state != State.RUNNING) return;

        // Determine which team's portal was scored
        String team = getTeam(player);
        String portalSide = (plugin.getConfig().getString("center.axis").equals("Z") ? loc.getBlockZ() : loc.getBlockX()) < plugin.getConfig().getInt("center.value") ? "red" : "blue";

        if (team.equals("red") && portalSide.equals("blue")) {
            redScore++;
            broadcast(ChatColor.RED + player.getName() + " scored for Team Red! " + ChatColor.RED + redScore + ChatColor.WHITE + " - " + ChatColor.BLUE + blueScore);
            for (Player p : Bukkit.getWorld("bridge").getPlayers()) respawnPlayer(p);
        } else if (team.equals("blue") && portalSide.equals("red")) {
            blueScore++;
            broadcast(ChatColor.BLUE + player.getName() + " scored for Team Blue! " + ChatColor.RED + redScore + ChatColor.WHITE + " - " + ChatColor.BLUE + blueScore);
            for (Player p : Bukkit.getWorld("bridge").getPlayers()) respawnPlayer(p);
        } else {
            broadcast(player.getName() + " has tried to scored in their own portal! Shame on them!");
            respawnPlayer(player);
        }

        // Display score when someone scores
        displayScore(team, player);
        updateScoreboard();
    }

    public void handlePlayerQuit(Player player) {
        if (state == State.RUNNING && getTeam(player).equals("none")) return;

        int playersAfterQuit = Bukkit.getWorld("bridge").getPlayers().size() - 1;

        if (state == State.RUNNING && playersAfterQuit < 2) {
            broadcast(ChatColor.RED + "Not Enough Players, ending game.");
            List<Player> remaining = Bukkit.getWorld("bridge").getPlayers();
            remaining.remove(player);
            if (!remaining.isEmpty()) endGame((getTeam(remaining.get(0)).equals("red") ? "Red" : "Blue"));
            else setState(State.WAITING);
        } else if (state == State.COUNTDOWN && playersAfterQuit < 2) setState(State.WAITING);
        
    }
    
    public State getState() {
        return state;
    }

    public void respawnPlayer(Player player) {
        // Reset health and food and saturation
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);

        // Reset player location
        String team = getTeam(player);
        Configuration config = plugin.getConfig();
        Location spawn = team.equals("red") ?
        new Location(Bukkit.getWorld("bridge"), config.getDouble("red.x"), config.getDouble("red.y"), config.getDouble("red.z"), (float) config.getDouble("red.yaw"), (float) config.getDouble("red.pitch")) :
        new Location(Bukkit.getWorld("bridge"), config.getDouble("blue.x"), config.getDouble("blue.y"), config.getDouble("blue.z"), (float) config.getDouble("blue.yaw"), (float) config.getDouble("blue.pitch"));
        player.teleport(spawn);

        // Clear Inventory to prevent stacking items in inventory's back slots
        player.getInventory().clear();

        // Clear effets like gapple
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());

        // Give Starting Inventory
        short clayColor = team.equals("red") ? (short) 14 : (short) 11;
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta pickaxeMeta = pickaxe.getItemMeta();
        pickaxeMeta.addEnchant(Enchantment.DIG_SPEED, 5, true);
        pickaxe.setItemMeta(pickaxeMeta);
        player.getInventory().setItem(1, pickaxe);
        player.getInventory().setItem(2, new ItemStack(Material.IRON_AXE));
        player.getInventory().setItem(3, new ItemStack(Material.BOW));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_BEEF, 8));
        player.getInventory().setItem(5, new ItemStack(Material.GOLDEN_APPLE, 8));
        for (int i = 6; i < 9; i++) {
            player.getInventory().setItem(i, new ItemStack(Material.STAINED_CLAY, 64, clayColor));
        }
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 64));
        player.getInventory().setItem(10, new ItemStack(Material.ARROW, 64));
    }

    public void stopLoop() {
        if (gameLoop != null && !gameLoop.isCancelled()) gameLoop.cancel();
    }

    private void displayScore(String team, Player player) {
        for (Player p : Bukkit.getWorld("bridge").getPlayers()) 
            p.sendTitle(ChatColor.RED + String.valueOf(redScore) + ChatColor.WHITE + " - " + ChatColor.BLUE + String.valueOf(blueScore),
        (team.equals("red") ? ChatColor.RED : ChatColor.BLUE) + player.getName() + " scored!",
        5, 30, 10);
    }

    private void resetWorld() {
        World world = Bukkit.getWorld("bridge");
        if (world == null) return;

        Bukkit.unloadWorld(world, false);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.deleteFolder(new File(Bukkit.getWorldContainer(), "bridge"));
            List<String> maps = plugin.getConfig().getStringList("maps");
            String map = maps.get(new Random().nextInt(maps.size()));
            plugin.copyFolder(
                new File(Bukkit.getWorldContainer(), map),
                new File(Bukkit.getWorldContainer(), "bridge")
            );

            Bukkit.getScheduler().runTask(plugin, () -> {
                new WorldCreator("bridge").environment(World.Environment.NORMAL).createWorld();
            });
        });
    }

    private void endGame(String winner) {
        setState(State.ENDED);
        for (Player p : Bukkit.getWorld("bridge").getPlayers()) {
            p.sendTitle((winner.equals("Red") ? ChatColor.RED : (winner.equals("Blue") ? ChatColor.BLUE : ChatColor.GRAY)) + winner + " Wins!", ChatColor.GOLD + "GG!", 10, 60, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            redScore = 0;
            blueScore = 0;
            countdownTimer = 10;
            teams.clear();

            for (Player p : Bukkit.getWorld("bridge").getPlayers()) {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                p.teleport(Bukkit.getWorld("world").getSpawnLocation());
            }
            Bukkit.getWorld("bridge").setPVP(false);
            resetWorld();
            setState(State.WAITING);
        }, 100L);
    }
    
    private void removeCages(Location center) {
        World world = center.getWorld();
        Configuration config = plugin.getConfig();
        // For each block in the cube
        for (int x = -config.getInt("cage-size.x-half-length"); x <= config.getInt("cage-size.x-half-length"); x++) {
            for (int y = 0; y <= config.getInt("cage-size.height"); y++) {
                for (int z = -config.getInt("cage-size.y-half-length"); z <= config.getInt("cage-size.y-half-length"); z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.STAINED_GLASS || block.getType() == Material.STAINED_CLAY) block.setType(Material.AIR);
                }
            }
        }
    }

    private void setState(State newState) {
        this.state = newState;
        // Setup Game
        if (newState == State.RUNNING) {
            // Assign Teams
            teams.clear();
            List<Player> players = new ArrayList<>(Bukkit.getWorld("bridge").getPlayers());
            Collections.shuffle(players);
            for (int i = 0; i < players.size(); i++) {
                String team = (i < players.size() / 2) ? "red" : "blue";
                Player player = players.get(i);
                player.sendMessage((team.equals("red") ? ChatColor.RED : ChatColor.BLUE) + "You are on Team " + (team.equals("red") ? "Red" : "Blue") + "!");
                teams.put(player.getUniqueId(), team);
                respawnPlayer(player);
            }

            // Reset and setup scoreboard
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = scoreboard.registerNewObjective("bridge", "dummy");
            objective.setDisplayName(ChatColor.GOLD + "The Bridge");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            new BukkitRunnable() {
                int count = 3;
                String[] messages = {"Get Ready!", "Get Set!"};

                @Override
                public void run() {
                    for (Player p : Bukkit.getWorld("bridge").getPlayers()) {
                        if (count > 1) {
                            p.sendTitle(ChatColor.YELLOW + messages[3 - count], "", 5, 18, 7);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        } else {
                            p.sendTitle(ChatColor.GREEN + "GOOO!", ChatColor.WHITE + "Bridge to the other side!", 5, 30, 10);
                            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_LAUNCH, 1f, 1f);
                        }
                    }

                    count --;
                    if (count <= 0) {
                        Configuration config = plugin.getConfig();
                        // Remove Red and Blue cages
                        removeCages(new Location(Bukkit.getWorld("bridge"), config.getInt("red-cage-location.x"), config.getInt("red-cage-location.y"), config.getInt("red-cage-location.z")));
                        removeCages(new Location(Bukkit.getWorld("bridge"), config.getInt("blue-cage-location.x"), config.getInt("blue-cage-location.y"), config.getInt("blue-cage-location.z")));
                        // Show starting score
                        updateScoreboard();
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);

            // Set gamemode
            for (Player p : Bukkit.getWorld("bridge").getPlayers()) p.setGameMode(GameMode.SURVIVAL);

            // Enable PVP
            Bukkit.getWorld("bridge").setPVP(true);
        }
    }

    public String getTeam(Player player) {
        return teams.getOrDefault(player.getUniqueId(), "none");
    }

    private void broadcast(String message) {
        for (Player p : Bukkit.getWorld("bridge").getPlayers()) p.sendMessage(message);
    }

    private void updateScoreboard() {
        objective.getScore(ChatColor.RED + "Red").setScore(redScore);
        objective.getScore(ChatColor.BLUE + "Blue").setScore(blueScore);
        for (Player p : Bukkit.getWorld("bridge").getPlayers()) {
            p.setScoreboard(scoreboard);
        }
    }
}
