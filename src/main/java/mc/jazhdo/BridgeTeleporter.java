package mc.jazhdo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BridgeTeleporter implements CommandExecutor {
    private final BridgeGame game;

    public BridgeTeleporter(BridgeGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (game.getState() == BridgeGame.State.RUNNING || game.getState() == BridgeGame.State.ENDED) player.sendMessage(ChatColor.RED + "A game is already in progress!");
        else player.sendMessage(ChatColor.GREEN + "You joined the Bridge game!");

        player.teleport(Bukkit.getWorld("bridge").getSpawnLocation());
        return true;
    }
}
