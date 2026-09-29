package com.yourname.wouldyourather;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WouldYouRather extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> lockedPlayers = new HashSet<>();
    private final Set<UUID> lavaWalkers = new HashSet<>();
    private final Component guiTitle = Component.text("Make a Choice...", NamedTextColor.DARK_RED, TextDecoration.BOLD);
    
    public enum GameMode { TIMER, ADVANCEMENT, OFF }
    private GameMode currentMode = GameMode.TIMER;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("wyr").setExecutor(this);
        
        // 5-Minute Global Timer Engine
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (currentMode == GameMode.TIMER) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    openWYRMenu(p);
                }
            }
        }, 6000L, 6000L); // 6000 ticks = 5 minutes

        // The Floor is Lava Tracking Engine (Runs every 2 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : lavaWalkers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    applyMagmaTrail(p);
                }
            }
        }, 0L, 2L);

        getLogger().info("WouldYouRather v1.1 enabled! Current mode: " + currentMode.name());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("mode")) {
            if (!sender.hasPermission("wouldyourather.admin")) return true;
            if (args.length == 2) {
                try {
                    currentMode = GameMode.valueOf(args[1].toUpperCase());
                    sender.sendMessage(Component.text("Gamemode set to: " + currentMode.name(), NamedTextColor.GREEN));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid mode. Use TIMER, ADVANCEMENT, or OFF.", NamedTextColor.RED));
                }
                return true;
            }
        }

        if (sender instanceof Player player) {
            openWYRMenu(player);
        }
        return true;
    }

    private void openWYRMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, guiTitle);

        ItemStack optionA = new ItemStack(Material.MAGMA_BLOCK);
        ItemMeta metaA = optionA.getItemMeta();
        metaA.displayName(Component.text("The Floor is Lava (30s)", NamedTextColor.GOLD));
        optionA.setItemMeta(metaA);

        ItemStack optionB = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        ItemMeta metaB = optionB.getItemMeta();
        metaB.displayName(Component.text("Lose All Your Armor", NamedTextColor.DARK_RED));
        optionB.setItemMeta(metaB);

        inv.setItem(11, optionA);
        inv.setItem(15, optionB);

        player.openInventory(inv);
        lockedPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
    }

    // Advancement Trigger Engine
    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (currentMode != GameMode.ADVANCEMENT) return;
        
        // Ignore background recipe unlocks
        if (event.getAdvancement().getKey().getKey().contains("recipes/")) return;
        
        // Delay by 1 tick so the vanilla advancement UI plays nicely before locking them
        Bukkit.getScheduler().runTaskLater(this, () -> {
            openWYRMenu(event.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(guiTitle)) return;
        event.setCancelled(true); 
        
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        lockedPlayers.remove(player.getUniqueId());
        player.closeInventory();

        if (clicked.getType() == Material.MAGMA_BLOCK) {
            // Option A: 30 Seconds of Magma
            Bukkit.broadcast(Component.text(player.getName() + " chose the Magma Trail!", NamedTextColor.GOLD));
            lavaWalkers.add(player.getUniqueId());
            
            // Remove them from the set after 30 seconds (600 ticks)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                lavaWalkers.remove(player.getUniqueId());
                player.sendMessage(Component.text("Your feet cool down. The curse is lifted.", NamedTextColor.GRAY));
            }, 600L);

        } else if (clicked.getType() == Material.CHAINMAIL_CHESTPLATE) {
            // Option B: Armor Obliteration
            Bukkit.broadcast(Component.text(player.getName() + "'s armor shattered!", NamedTextColor.DARK_RED));
            player.getInventory().setArmorContents(null);
            
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getView().title().equals(guiTitle) && lockedPlayers.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(this, () -> openWYRMenu(player));
        }
    }

    private void applyMagmaTrail(Player player) {
        Block blockUnder = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        
        // Only convert solid blocks that aren't already magma or bedrock
        if (blockUnder.getType().isSolid() && blockUnder.getType() != Material.MAGMA_BLOCK && blockUnder.getType() != Material.BEDROCK) {
            
            BlockData originalData = blockUnder.getBlockData();
            blockUnder.setType(Material.MAGMA_BLOCK);
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, blockUnder.getLocation().add(0.5, 1, 0.5), 2, 0.1, 0, 0.1, 0.05);

            // Safe-state reversion after 5 seconds
            Bukkit.getScheduler().runTaskLater(this, () -> {
                blockUnder.setBlockData(originalData);
            }, 100L);
        }
    }
}
