
package br.kart.sell;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KartSell extends JavaPlugin implements Listener, CommandExecutor {

    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (provider != null) {
            economy = provider.getProvider();
        }

        if (getCommand("sell") != null) {
            getCommand("sell").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KartSell ativado.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }

        if (!player.hasPermission("kartsell.use")) {
            player.sendMessage(color("&cVocê não tem permissão."));
            return true;
        }

        if (economy == null) {
            player.sendMessage(color("&cVault ou um plugin de economia não está instalado."));
            return true;
        }

        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("menu.rows", 6)));
        String title = color(getConfig().getString(
                "menu.title", "&8KartSell &7- &fVender itens"));

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(color("&6&lKartSell"));
        infoMeta.setLore(List.of(
                color("&7Venda os itens do seu inventário."),
                color("&7Itens bloqueados não serão vendidos."),
                color("&eClique no botão abaixo para vender.")
        ));
        info.setItemMeta(infoMeta);

        int infoSlot = getConfig().getInt("menu.info-slot", 4);
        if (infoSlot >= 0 && infoSlot < inventory.getSize()) {
            inventory.setItem(infoSlot, info);
        }

        ItemStack sell = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.setDisplayName(color("&a&lVENDER INVENTÁRIO"));
        sellMeta.setLore(List.of(
                color("&7Vende todos os itens elegíveis."),
                color("&eClique para confirmar a venda.")
        ));
        sell.setItemMeta(sellMeta);

        int sellSlot = getConfig().getInt(
                "menu.sell-all-slot", inventory.getSize() - 5);

        if (sellSlot >= 0 && sellSlot < inventory.getSize()) {
            inventory.setItem(sellSlot, sell);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = color(getConfig().getString(
                "menu.title", "&8KartSell &7- &fVender itens"));

        if (!event.getView().getTitle().equals(title)) return;

        event.setCancelled(true);

        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int sellSlot = getConfig().getInt(
                "menu.sell-all-slot",
                event.getView().getTopInventory().getSize() - 5);

        if (event.getRawSlot() == sellSlot) {
            player.closeInventory();
            sellInventory(player);
        }
    }

    private void sellInventory(Player player) {
        if (economy == null) {
            player.sendMessage(color("&cSistema de economia indisponível."));
            return;
        }

        double total = 0.0;
        boolean soldAnything = false;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (isBlocked(item.getType())) continue;

            double unitPrice = getPrice(item);
            if (unitPrice <= 0) continue;

            total += unitPrice * item.getAmount();
            soldAnything = true;
            item.setAmount(0);
        }

        if (!soldAnything || total <= 0) {
            player.sendMessage(color("&cVocê não possui itens vendáveis."));
            return;
        }

        economy.depositPlayer(player, total);
        player.sendMessage(color("&6&lKartSell &8» &aVocê vendeu seus itens por &e$"
                + String.format(Locale.US, "%.2f", total) + "&a."));
    }

    private double getPrice(ItemStack item) {
        String material = item.getType().name();

        ConfigurationSection special =
                getConfig().getConfigurationSection("enchanted-prices." + material);

        if (special != null && item.getItemMeta() != null
                && item.getItemMeta().hasEnchants()) {
            for (var enchantment : item.getEnchantments().keySet()) {
                String enchantName = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);

                if (special.contains(enchantName)) {
                    return special.getDouble(enchantName);
                }
            }
        }

        return getConfig().getDouble("prices." + material,
                getConfig().getDouble("default-price", 1.0));
    }

    private boolean isBlocked(Material material) {
        List<String> blocked = getConfig().getStringList("blocked-items");
        return blocked.contains(material.name());
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
