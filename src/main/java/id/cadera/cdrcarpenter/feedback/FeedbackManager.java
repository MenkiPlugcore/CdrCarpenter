package id.cadera.cdrcarpenter.feedback;

import id.cadera.cdrcarpenter.CdrCarpenter;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class FeedbackManager {
    private final CdrCarpenter plugin;

    public FeedbackManager(CdrCarpenter plugin) {
        this.plugin = plugin;
    }

    public void furniturePlaced(Player player, Location location) {
        if (sound("furniture.sound")) {
            player.playSound(location, Sound.BLOCK_WOOD_PLACE, 0.65F, 0.95F);
        }
        if (particles("furniture.particles")) {
            woodParticles(location.clone().add(0.0D, 0.15D, 0.0D), 8, 0.20D);
        }
    }

    public void furniturePickedUp(Player player, Location location) {
        if (sound("furniture.sound")) {
            player.playSound(location, Sound.BLOCK_WOOD_BREAK, 0.55F, 1.10F);
            player.playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.35F, 1.15F);
        }
        if (particles("furniture.particles")) {
            woodParticles(location.clone().add(0.0D, 0.25D, 0.0D), 6, 0.18D);
        }
    }

    public void workbenchCraft(Player player, String displayName) {
        Location location = player.getLocation().clone();
        for (int i = 0; i < 3; i++) {
            long delay = i * 4L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (sound("workbench.sound")) {
                    player.playSound(location, Sound.BLOCK_WOOD_PLACE, 0.70F, 0.75F + (delay / 40.0F));
                }
                if (particles("workbench.particles")) {
                    woodParticles(location.clone().add(0.0D, 1.0D, 0.0D), 7, 0.28D);
                }
            }, delay);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (sound("workbench.sound")) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.55F, 1.25F);
            }
            if (actionBar("workbench.actionbar")) {
                player.sendActionBar(Component.text("Craft complete: " + displayName));
            }
        }, 10L);
    }

    public void workbenchFailure(Player player) {
        if (sound("workbench.sound")) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45F, 0.75F);
        }
    }

    public void blueprintLearned(Player player, String displayName) {
        Location location = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        if (sound("blueprint.sound")) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.75F, 1.05F);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45F, 1.45F);
                }
            }, 4L);
        }
        if (particles("blueprint.particles")) {
            player.getWorld().spawnParticle(Particle.END_ROD, location, 18, 0.38D, 0.45D, 0.38D, 0.02D);
        }
        if (actionBar("blueprint.actionbar")) {
            player.sendActionBar(Component.text("Blueprint learned: " + displayName));
        }
    }

    public void blueprintAlreadyKnown(Player player) {
        if (sound("blueprint.sound")) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.45F, 0.80F);
        }
    }

    public void sawmillStart(Player player, Location location) {
        if (sound("sawmill.sound")) {
            player.playSound(location, Sound.BLOCK_STONECUTTER_USE, 0.80F, 0.75F);
        }
        if (particles("sawmill.particles")) {
            woodParticles(location.clone().add(0.0D, 0.85D, 0.0D), 10, 0.25D);
        }
        if (actionBar("sawmill.actionbar")) {
            player.sendActionBar(Component.text("Sawmill started"));
        }
    }

    public void sawmillPulse(Player player, Location location, int progressPercent) {
        int clamped = Math.max(0, Math.min(100, progressPercent));
        if (sound("sawmill.sound")) {
            float pitch = 0.78F + (clamped / 100.0F) * 0.18F;
            player.playSound(location, Sound.BLOCK_STONECUTTER_USE, 0.48F, pitch);
        }
        if (particles("sawmill.particles")) {
            woodParticles(location.clone().add(0.0D, 0.90D, 0.0D), 7, 0.22D);
        }
        if (actionBar("sawmill.actionbar")) {
            player.sendActionBar(Component.text("Sawmill: " + clamped + "%"));
        }
    }

    public void sawmillFinish(Player player, Location location, int outputAmount) {
        if (sound("sawmill.sound")) {
            player.playSound(location, Sound.BLOCK_STONECUTTER_USE, 0.70F, 1.15F);
            player.playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.55F, 1.20F);
        }
        if (particles("sawmill.particles")) {
            woodParticles(location.clone().add(0.0D, 0.90D, 0.0D), 18, 0.35D);
        }
        if (actionBar("sawmill.actionbar")) {
            player.sendActionBar(Component.text("Sawmill complete: " + outputAmount + " Processed Wood"));
        }
    }

    public boolean sawmillModelAnimationEnabled() {
        return enabled("effects.sawmill.animate-model", true);
    }

    private void woodParticles(Location location, int count, double spread) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(
                Particle.BLOCK,
                location,
                count,
                spread,
                spread * 0.55D,
                spread,
                0.025D,
                Material.OAK_PLANKS.createBlockData()
        );
    }

    private boolean sound(String path) {
        return enabled("effects." + path, true);
    }

    private boolean particles(String path) {
        return enabled("effects." + path, true);
    }

    private boolean actionBar(String path) {
        return enabled("effects." + path, true);
    }

    private boolean enabled(String path, boolean fallback) {
        return plugin.getConfig().getBoolean("effects.enabled", true)
                && plugin.getConfig().getBoolean(path, fallback);
    }
}
