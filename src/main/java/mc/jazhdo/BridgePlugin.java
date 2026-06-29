package mc.jazhdo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public class BridgePlugin extends JavaPlugin {
    private BridgeGame game;

    public void copyFolder(File src, File dest) {
        dest.mkdirs();
        for (File f : src.listFiles()) {
            if (f.isDirectory()) copyFolder(f, new File(dest, f.getName()));
            else {
                try {
                    java.nio.file.Files.copy(f.toPath(), new File(dest, f.getName()).toPath());
                } catch (IOException e) {
                    getLogger().warning("Error occurred while copying files: ".concat(e.getMessage()));
                }
            }
        }
        File uidFile = new File(dest, "uid.dat");
        if (uidFile.exists()) uidFile.delete();
    }

    public void deleteFolder(File folder) {
        File[] fileList = folder.listFiles();
        if (fileList != null) 
            for (File file : fileList) 
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
        folder.delete();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File bridgeFolder = new File(Bukkit.getWorldContainer(), "bridge");
        List<String> maps = getConfig().getStringList("maps");
        String map = maps.get(new Random().nextInt(maps.size()));
        File backupFolder = new File(Bukkit.getWorldContainer(), map);

        World existing = Bukkit.getWorld("bridge");
        if (existing != null) Bukkit.unloadWorld(existing, false);

        if (bridgeFolder.exists()) deleteFolder(bridgeFolder);
        copyFolder(backupFolder, bridgeFolder);

        new WorldCreator("bridge").environment(World.Environment.NORMAL).createWorld();

        game = new BridgeGame(this);
        game.startLoop();
        getServer().getPluginManager().registerEvents(new BridgeListener(game, this), this);
        getCommand("bridge").setExecutor(new BridgeTeleporter(game));
    }

    @Override
    public void onDisable() {
        if (game != null) game.stopLoop();
    }
}
