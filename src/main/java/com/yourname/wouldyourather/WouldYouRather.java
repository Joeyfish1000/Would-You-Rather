package com.yourname.wouldyourather;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;

public class WouldYouRather extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> lavaWalkers = new HashSet<>();
    private final Component guiTitle = Component.text("VOTE: WOULD YOU RATHER...", NamedTextColor.DARK_RED, TextDecoration.BOLD);
    
    public enum GameMode { TIMER, ADVANCEMENT, OFF }
    private GameMode currentMode = GameMode.TIMER;

    // Voting State
    private boolean isVoting = false;
    private Dilemma currentDilemma = null;
    private final Map<UUID, Integer> activeVotes = new HashMap<>(); // 1 for A, 2 for B
    private int votingTask = -1;
    private int voteTimeRemaining = 15;

    private final List<Dilemma> arsenal = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("wyr").setExecutor(this);
        
        registerDilemmas();

        // 5-Minute Global Timer Engine
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (currentMode == GameMode.TIMER && !isVoting && Bukkit.getOnlinePlayers().size() > 0) {
                startGlobalVote();
            }
        }, 6000L, 6000L); 

        // The Floor is Lava Tracking Engine
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : lavaWalkers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    applyMagmaTrail(p);
                }
            }
        }, 0L, 2L);

        getLogger().info("WouldYouRather v1.2 loaded! 8 Dilemmas Ready.");
    }

    private void registerDilemmas() {
        // 1. Lava vs Armor
        arsenal.add(new Dilemma(
            "Floor is Lava (30s)", Material.MAGMA_BLOCK, 
            "Lose All Armor", Material.CHAINMAIL_CHESTPLATE,
            players -> {
                for(Player p : players) lavaWalkers.add(p.getUniqueId());
                Bukkit.getScheduler().runTaskLater(this, () -> lavaWalkers.clear(), 600L);
            },
            players -> {
                for(Player p : players) {
                    p.getInventory().setArmorContents(null);
                    p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.5f);
                    p.getWorld().spawnParticle(Particle.LAVA, p.getLocation().add(0, 1, 0), 30);
                }
            }
        ));

        // 2. Levitation vs Anvils
        arsenal.add(new Dilemma(
            "Server Levitation (10s)", Material.FEATHER, 
            "Anvil to the Head", Material.ANVIL,
            players -> {
                for(Player p : players) p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1));
            },
            players -> {
                for(Player p : players) {
                    FallingBlock anvil = p.getWorld().spawnFallingBlock(p.getLocation().add(0, 10, 0), Material.ANVIL.createBlockData());
                    anvil.setDropItem(false); anvil.setHurtEntities(true);
                    p.setVelocity(new Vector(0, -1, 0));
                }
            }
        ));

        // 3. Diamonds vs Wither
        arsenal.add(new Dilemma(
            "Get 10 Diamonds", Material.DIAMOND, 
            "Spawn Wither at Spawn", Material.WITHER_SKELETON_SKULL,
            players -> {
                for(Player p : players) p.getInventory().addItem(new ItemStack(Material.DIAMOND, 10));
            },
            players -> {
                World w = Bukkit.getWorlds().get(0);
                w.spawnEntity(w.getSpawnLocation(), EntityType.WITHER);
                Bukkit.broadcast(Component.text("A WITHER HAS AWOKEN AT SPAWN!", NamedTextColor.DARK_RED));
            }
        ));

        // 4. Zombies vs Ravager
        arsenal.add(new Dilemma(
            "Fight 10 Baby Zombies", Material.ZOMBIE_HEAD, 
            "Fight a Speed II Ravager", Material.RAVAGER_SPAWN_EGG,
            players -> {
                for(Player p : players) {
                    for(int i=0; i<10; i++) p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE);
                }
            },
            players -> {
                for(Player p : players) {
                    org.bukkit.entity.Ravager r = (org.bukkit.entity.Ravager) p.getWorld().spawnEntity(p.getLocation(), EntityType.RAVAGER);
                    r.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9999, 1));
                }
            }
        ));

        // 5. Blindness vs Nausea
        arsenal.add(new Dilemma(
            "Blindness (30s)", Material.BLACK_DYE, 
            "Nausea (30s)", Material.PUFFERFISH,
            players -> {
                for(Player p : players) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 0));
            },
            players -> {
                for(Player p : players) p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 600, 0));
            }
        ));

        // 6. Drop Items vs Random Teleport
        arsenal.add(new Dilemma(
            "Force Drop Held Item", Material.DROPPER, 
            "Random 1000 Block TP", Material.ENDER_PEARL,
            players -> {
                for(Player p : players) p.dropItem(false);
            },
            players -> {
                for(Player p : players) {
                    Location loc = p.getLocation();
                    loc.add(random.nextInt(2000) - 1000, 0, random.nextInt(2000) - 1000);
                    loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
                    p.teleport(loc);
                    p.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }
            }
        ));

        // 7. Creepers vs Random Death
        arsenal.add(new Dilemma(
            "Spawn Creeper Behind You", Material.CREEPER_HEAD, 
            "One Random Player Dies", Material.SKELETON_SKULL,
            players -> {
                for(Player p : players) {
                    Location behind = p.getLocation().subtract(p.getLocation().getDirection().multiply(2));
                    p.getWorld().spawnEntity(behind, EntityType.CREEPER);
                }
            },
            players -> {
                if(players.isEmpty()) return;
                Player victim = players.get(random.nextInt(players.size()));
                victim.setHealth(0);
                Bukkit.broadcast(Component.text("The server sacrificed " + victim.getName() + "!", NamedTextColor.RED));
            }
        ));

        // 8. Clear Weather vs Thunderstorm
        arsenal.add(new Dilemma(
            "Clear Weather", Material.SUNFLOWER, 
            "Eternal Thunderstorm", Material.LIGHTNING_ROD,
            players -> {
                Bukkit.getWorlds().get(0).setStorm(false);
            },
            players -> {
                Bukkit.getWorlds().get(0).setStorm(true);
                Bukkit.getWorlds().get(0).setThundering(true);
            }
        ));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("force") && sender.hasPermission("wouldyourather.admin")) {
                if (!isVoting) startGlobalVote();
                return true;
            }
            if (args[0].equalsIgnoreCase("mode") && sender.hasPermission("wouldyourather.admin")) {
                if (args.length == 2) {
                    try {
                        currentMode = GameMode.valueOf(args[1].toUpperCase());
                        sender.sendMessage(Component.text("Gamemode set to: " + currentMode.name(), NamedTextColor.GREEN));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text("Invalid mode.", NamedTextColor.RED));
                    }
                    return true;
                }
            }
        }
        return true;
    }

    // Advancement Trigger Engine
    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (currentMode != GameMode.ADVANCEMENT || isVoting) return;
        if (event.getAdvancement().getKey().getKey().contains("recipes/")) return;
        
        Bukkit.getScheduler().runTaskLater(this, this::startGlobalVote, 1L);
    }

    private void startGlobalVote() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        
        isVoting = true;
        activeVotes.clear();
        voteTimeRemaining = 15;
        currentDilemma = arsenal.get(random.nextInt(arsenal.size()));

        for (Player p : Bukkit.getOnlinePlayers()) openVoteGUI(p);
        Bukkit.broadcast(Component.text("A NEW DILEMMA HAS APPEARED! You have 15 seconds to vote.", NamedTextColor.RED, TextDecoration.BOLD));

        votingTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            voteTimeRemaining--;
            
            // Check if everyone has voted
            if (activeVotes.size() >= Bukkit.getOnlinePlayers().size() || voteTimeRemaining <= 0) {
                endGlobalVote();
            }
        }, 20L, 20L).getTaskId();
    }

    private void openVoteGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, guiTitle);

        ItemStack optA = new ItemStack(currentDilemma.matA);
        ItemMeta metaA = optA.getItemMeta();
        metaA.displayName(Component.text(currentDilemma.nameA, NamedTextColor.GOLD));
        optA.setItemMeta(metaA);

        ItemStack optB = new ItemStack(currentDilemma.matB);
        ItemMeta metaB = optB.getItemMeta();
        metaB.displayName(Component.text(currentDilemma.nameB, NamedTextColor.DARK_RED));
        optB.setItemMeta(metaB);

        inv.setItem(11, optA);
        inv.setItem(15, optB);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(guiTitle) || !isVoting) return;
        event.setCancelled(true); 
        
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == currentDilemma.matA) {
            activeVotes.put(player.getUniqueId(), 1);
            player.sendMessage(Component.text("You voted for: " + currentDilemma.nameA, NamedTextColor.GREEN));
        } else if (clicked.getType() == currentDilemma.matB) {
            activeVotes.put(player.getUniqueId(), 2);
            player.sendMessage(Component.text("You voted for: " + currentDilemma.nameB, NamedTextColor.GREEN));
        }
        
        // Change title to show they voted (requires closing and reopening a dummy inventory, or just locking them out of clicks)
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (isVoting && event.getView().title().equals(guiTitle)) {
            // Force it back open if the voting phase is still active
            Bukkit.getScheduler().runTask(this, () -> openVoteGUI(player));
        }
    }

    private void endGlobalVote() {
        Bukkit.getScheduler().cancelTask(votingTask);
        isVoting = false;

        int votesA = 0, votesB = 0;
        for (int vote : activeVotes.values()) {
            if (vote == 1) votesA++;
            else if (vote == 2) votesB++;
        }

        int winningOption = 0;
        if (votesA > votesB) winningOption = 1;
        else if (votesB > votesA) winningOption = 2;
        else winningOption = random.nextBoolean() ? 1 : 2; // Tie breaker

        // Release players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (winningOption == 1) {
            Bukkit.broadcast(Component.text("The Server chose: " + currentDilemma.nameA + " (" + votesA + " to " + votesB + ")", NamedTextColor.GOLD));
            currentDilemma.actionA.accept(targets);
        } else {
            Bukkit.broadcast(Component.text("The Server chose: " + currentDilemma.nameB + " (" + votesB + " to " + votesA + ")", NamedTextColor.DARK_RED));
            currentDilemma.actionB.accept(targets);
        }
    }

    private void applyMagmaTrail(Player player) {
        Block blockUnder = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (blockUnder.getType().isSolid() && blockUnder.getType() != Material.MAGMA_BLOCK && blockUnder.getType() != Material.BEDROCK) {
            BlockData originalData = blockUnder.getBlockData();
            blockUnder.setType(Material.MAGMA_BLOCK);
            player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, blockUnder.getLocation().add(0.5, 1, 0.5), 2);

            Bukkit.getScheduler().runTaskLater(this, () -> blockUnder.setBlockData(originalData), 100L);
        }
    }

    // Helper Class for Arsenal
    private static class Dilemma {
        String nameA, nameB;
        Material matA, matB;
        Consumer<List<Player>> actionA, actionB;

        Dilemma(String nameA, Material matA, String nameB, Material matB, Consumer<List<Player>> actionA, Consumer<List<Player>> actionB) {
            this.nameA = nameA; this.matA = matA;
            this.nameB = nameB; this.matB = matB;
            this.actionA = actionA; this.actionB = actionB;
        }
    }
}
