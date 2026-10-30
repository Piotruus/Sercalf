package pl.piotruusmc.lifesteal;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LifeStealPlugin extends JavaPlugin implements Listener {
    private NamespacedKey heartKey;
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        heartKey = new NamespacedKey(this, "physical_heart");
        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Nie mozna utworzyc players.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LifeSteal z fizycznymi sercami wlaczony.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private int hearts(Player p) {
        return data.getInt("players." + p.getUniqueId() + ".hearts", getConfig().getInt("default-hearts", 10));
    }

    private void setHearts(Player p, int amount) {
        int min = getConfig().getInt("minimum-hearts", 1);
        int max = getConfig().getInt("maximum-hearts", 20);
        amount = Math.max(min, Math.min(max, amount));
        data.set("players." + p.getUniqueId() + ".name", p.getName());
        data.set("players." + p.getUniqueId() + ".hearts", amount);
        applyHealth(p);
    }

    private void applyHealth(Player p) {
        if (p.getAttribute(Attribute.MAX_HEALTH) == null) return;
        p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hearts(p) * 2.0);
        if (p.getHealth() > p.getAttribute(Attribute.MAX_HEALTH).getValue()) {
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
    }

    private ItemStack heartItem(int amount) {
        Material material;
        try {
            material = Material.valueOf(getConfig().getString("heart-item-material", "RED_DYE").toUpperCase());
        } catch (IllegalArgumentException ex) {
            material = Material.RED_DYE;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("heart-name", "&c❤ &lSERCE")));
            List<String> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("heart-lore")) lore.add(color(line));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(heartKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isHeart(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(heartKey, PersistentDataType.BYTE);
    }

    private int countHearts(Player p) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (isHeart(item)) count += item.getAmount();
        }
        return count;
    }

    private void giveHearts(Player p, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(64, left);
            ItemStack item = heartItem(stack);
            var overflow = p.getInventory().addItem(item);
            overflow.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
            left -= stack;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        int current = hearts(victim);
        int min = getConfig().getInt("minimum-hearts", 1);
        if (current > min) {
            setHearts(victim, current - 1);
            // Physical heart drops into the world; it is not credited to killer automatically.
            event.getDrops().add(heartItem(1));
            victim.sendMessage(color("&cStraciles 1 serce! &7Wypadlo z Ciebie fizyczne serce."));
            killer.sendMessage(color("&aPokonales &f" + victim.getName() + "&a. Zdobadz serce, podnoszac je z ziemi!"));
        } else if (getConfig().getBoolean("ban-at-minimum", false)) {
            Bukkit.getBanList(BanList.Type.NAME).addBan(victim.getName(),
                    getConfig().getString("elimination-message", "Utraciles wszystkie serca!"), null, "LifeSteal");
            Bukkit.getScheduler().runTask(this, () -> victim.kickPlayer(color("&cUtraciles wszystkie serca!")));
        } else {
            victim.sendMessage(color("&cMasz juz minimalna liczbe serc."));
        }
        saveData();
    }

    @EventHandler
    public void onUseHeart(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!isHeart(item)) return;
        Player player = event.getPlayer();
        int current = hearts(player);
        int max = getConfig().getInt("maximum-hearts", 20);
        if (current >= max) {
            player.sendMessage(color("&cMasz juz maksymalna liczbe serc!"));
            return;
        }
        event.setCancelled(true);
        setHearts(player, current + 1);
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
        player.sendMessage(color("&aUzyles serca! Masz teraz &c" + hearts(player) + " &aserce/serc."));
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("sercailosc")) {
            if (args.length != 1) {
                sender.sendMessage(color("&cUzycie: /sercailosc <gracz>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(color("&cTen gracz musi byc online."));
                return true;
            }
            sender.sendMessage(color("&d" + target.getName() + "&7: &c" + hearts(target) +
                    " &7serc zycia, fizyczne serca w ekwipunku: &c" + countHearts(target)));
            return true;
        }

        if (cmd.equals("wyplacserca")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Tej komendy moze uzyc tylko gracz.");
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(color("&cUzycie: /wyplacserca <ilosc>"));
                return true;
            }
            int amount;
            try { amount = Integer.parseInt(args[0]); }
            catch (NumberFormatException ex) {
                p.sendMessage(color("&cPodaj prawidlowa liczbe."));
                return true;
            }
            int min = getConfig().getInt("minimum-hearts", 1);
            if (amount < 1 || hearts(p) - amount < min) {
                p.sendMessage(color("&cNie mozesz wyplacic tylu serc. Minimum: " + min + "."));
                return true;
            }
            setHearts(p, hearts(p) - amount);
            giveHearts(p, amount);
            p.sendMessage(color("&aWyplacono &c" + amount + " &aserce/serc jako przedmioty."));
            saveData();
            return true;
        }

        if (cmd.equals("sercadaj")) {
            if (!sender.hasPermission("lifesteal.admin")) {
                sender.sendMessage(color("&cBrak uprawnien."));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(color("&cUzycie: /sercadaj <gracz> <ilosc>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(color("&cGracz musi byc online."));
                return true;
            }
            int amount;
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) {
                sender.sendMessage(color("&cPodaj prawidlowa liczbe."));
                return true;
            }
            if (amount < 1 || amount > 4096) {
                sender.sendMessage(color("&cIlosc musi byc od 1 do 4096."));
                return true;
            }
            giveHearts(target, amount);
            sender.sendMessage(color("&aDales " + amount + " fizycznych serc graczowi " + target.getName() + "."));
            target.sendMessage(color("&aOtrzymales &c" + amount + " &afizycznych serc od administracji."));
            return true;
        }

        if (cmd.equals("sercazabierz")) {
            if (!sender.hasPermission("lifesteal.admin")) {
                sender.sendMessage(color("&cBrak uprawnien."));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(color("&cUzycie: /sercazabierz <gracz> <ilosc>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(color("&cGracz musi byc online."));
                return true;
            }
            int amount;
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) {
                sender.sendMessage(color("&cPodaj prawidlowa liczbe."));
                return true;
            }
            int removed = removeHeartItems(target, Math.max(1, amount));
            sender.sendMessage(color("&aZabrano " + removed + " fizycznych serc graczowi " + target.getName() + "."));
            return true;
        }

        if (cmd.equals("sercaset")) {
            if (!sender.hasPermission("lifesteal.admin")) {
                sender.sendMessage(color("&cBrak uprawnien."));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(color("&cUzycie: /sercaset <gracz> <ilosc>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(color("&cGracz musi byc online."));
                return true;
            }
            int amount;
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) {
                sender.sendMessage(color("&cPodaj prawidlowa liczbe."));
                return true;
            }
            setHearts(target, amount);
            saveData();
            sender.sendMessage(color("&aUstawiono serca zycia gracza " + target.getName() + " na " + hearts(target) + "."));
            return true;
        }
        return false;
    }

    private int removeHeartItems(Player p, int amount) {
        int left = amount;
        for (int i = 0; i < p.getInventory().getSize() && left > 0; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (!isHeart(item)) continue;
            int take = Math.min(left, item.getAmount());
            left -= take;
            if (take == item.getAmount()) p.getInventory().setItem(i, null);
            else item.setAmount(item.getAmount() - take);
        }
        return amount - left;
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try { data.save(dataFile); }
        catch (IOException e) { getLogger().severe("Nie mozna zapisac players.yml: " + e.getMessage()); }
    }
}
