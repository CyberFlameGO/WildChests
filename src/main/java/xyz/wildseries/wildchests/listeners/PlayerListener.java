package xyz.wildseries.wildchests.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.wildseries.wildchests.Locale;
import xyz.wildseries.wildchests.Updater;
import xyz.wildseries.wildchests.WildChestsPlugin;

@SuppressWarnings("unused")
public final class PlayerListener implements Listener {

    private WildChestsPlugin plugin;

    public PlayerListener(WildChestsPlugin plugin){
        this.plugin = plugin;
    }

    /*
    Just notifies me if the server is using WildBuster
     */

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e){
        if(e.getPlayer().getUniqueId().toString().equals("45713654-41bf-45a1-aa6f-00fe6598703b")){
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                e.getPlayer().sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.WHITE + "WildSeries" + ChatColor.DARK_GRAY + "] " +
                        ChatColor.GRAY + "This server is using WildChests v" + plugin.getDescription().getVersion()), 5L);
        }

        if(e.getPlayer().isOp() && Updater.isOutdated()){
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "WildChests" +
                        ChatColor.GRAY + " A new version is available (v" + Updater.getLatestVersion() + ")!"), 20L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int moneyEarned = plugin.getProviders().tryDepositMoney(e.getPlayer());
            if(moneyEarned > 0){
                Locale.MONEY_EARNED_OFFLINE.send(e.getPlayer(), moneyEarned);
            }
        }, 20L);

    }

}
