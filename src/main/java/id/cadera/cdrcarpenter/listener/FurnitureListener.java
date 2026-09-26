package id.cadera.cdrcarpenter.listener;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureItemFactory;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class FurnitureListener implements Listener {
    private final CdrCarpenter plugin;
    private final FurnitureManager manager;
    private final FurnitureRegistry registry;

    public FurnitureListener(CdrCarpenter plugin, FurnitureManager manager, FurnitureRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Block clicked = event.getClickedBlock();

        // Barrier-backed furniture intercepts the client's block ray trace before the display entity.
        if (clicked.getType() == Material.BARRIER) {
            Interaction furniture = manager.findFurnitureAtBarrier(clicked.getLocation());
            if (furniture != null) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    if (plugin.getConfig().getBoolean("placement.left-click-pickup", true)) {
                        manager.pickup(event.getPlayer(), furniture);
                    }
                    return;
                }

                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    boolean requireSneak = plugin.getConfig().getBoolean("placement.pickup-requires-sneak", true);
                    if (!requireSneak || event.getPlayer().isSneaking()) {
                        manager.pickup(event.getPlayer(), furniture);
                    }
                    // Non-sneak right click is reserved for the seat/workstation interaction phase.
                    return;
                }
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }

        String id = FurnitureItemFactory.getFurnitureId(plugin, event.getItem());
        if (id == null) {
            return;
        }

        FurnitureDefinition definition = registry.get(id);
        if (definition == null) {
            event.getPlayer().sendMessage("§cUnknown furniture id: " + id);
            event.setCancelled(true);
            return;
        }

        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isPassable()) {
            event.getPlayer().sendMessage("§cThere is not enough room to place furniture there.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        manager.place(event.getPlayer(), definition, target.getLocation(), event.getHand());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction) || !manager.isFurnitureEntity(interaction)) {
            return;
        }

        boolean requireSneak = plugin.getConfig().getBoolean("placement.pickup-requires-sneak", true);
        if (requireSneak && !event.getPlayer().isSneaking()) {
            // Reserved for future chair/workbench/sawmill interactions.
            return;
        }

        event.setCancelled(true);
        manager.pickup(event.getPlayer(), interaction);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureDamage(EntityDamageByEntityEvent event) {
        if (!manager.isFurnitureEntity(event.getEntity())) {
            return;
        }

        event.setCancelled(true);

        if (event.getDamager() instanceof Player player
                && plugin.getConfig().getBoolean("placement.left-click-pickup", true)) {
            manager.pickup(player, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBarrierBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BARRIER) {
            return;
        }

        Interaction furniture = manager.findFurnitureAtBarrier(event.getBlock().getLocation());
        if (furniture == null) {
            return;
        }

        event.setCancelled(true);
        manager.pickup(event.getPlayer(), furniture);
    }
}
