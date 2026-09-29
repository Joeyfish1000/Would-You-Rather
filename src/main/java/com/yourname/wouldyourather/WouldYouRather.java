package com.yourname.wouldyourather;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WouldYouRather extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> lockedPlayers = new HashSet<>();
    private final Component guiTitle = Component.text("Gravity's Joke...", NamedTextColor.DARK_RED, TextDecoration.BOLD);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("wyr").setExecutor(this);
        getLogger().info("WouldYouRather enabled! Ready to ruin some friendships.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        openWYRMenu(player);
        return true;
    }

    private void openWYRMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, guiTitle);

        ItemStack optionA = new ItemStack(Material.FEATHER);
        ItemMeta metaA = optionA.getItemMeta();
        metaA.displayName(Component.text("Everyone Gets Levitation (10s)", NamedTextColor.AQUA));
        optionA.setItemMeta(metaA);

        ItemStack optionB = new ItemStack(Material.ANVIL);
        ItemMeta metaB = optionB.getItemMeta();
        metaB.displayName(Component.text("Take an Anvil to the Head", NamedTextColor.RED));
        optionB.setItemMeta(metaB);

        inv.setItem(11, optionA);
        inv.setItem(15, optionB);

        player.openInventory(inv);
        lockedPlayers.add(player.getUniqueId());
        
        // Ominous sound on open
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(guiTitle)) return;
        
        event.setCancelled(true); // Prevent them from stealing the anvil/feather
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // They made a choice, release the lock!
        lockedPlayers.remove(player.getUniqueId());
        player.closeInventory();

        if (clicked.getType() == Material.FEATHER) {
            // Option A: Levitation for everyone
            Bukkit.broadcast(Component.text(player.getName() + " chose floating for everyone!", NamedTextColor.AQUA));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.0f);
            }
        } else if (clicked.getType() == Material.ANVIL) {
            // Option B: Anvil to the head
            Bukkit.broadcast(Component.text(player.getName() + " took the anvil!", NamedTextColor.RED));
            
            // Spawn lethal falling block
            FallingBlock anvil = player.getWorld().spawnFallingBlock(
                    player.getLocation().add(0, 10, 0),
                    Material.ANVIL.createBlockData()
            );
            anvil.setDropItem(false);
            anvil.setHurtEntities(true);
            
            // Apply physics & visuals
            player.setVelocity(new Vector(0, -1, 0));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        
        // If they try to hit ESC without picking, force it back open
        if (event.getView().title().equals(guiTitle) && lockedPlayers.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(this, () -> openWYRMenu(player));
        }
    }
}
