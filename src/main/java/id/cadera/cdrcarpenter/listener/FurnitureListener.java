package id.cadera.cdrcarpenter.listener;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.crafting.SawmillManager;
import id.cadera.cdrcarpenter.crafting.WorkbenchManager;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureItemFactory;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import id.cadera.cdrcarpenter.seating.SeatingManager;
import id.cadera.cdrcarpenter.storage.StorageManager;
import org.bukkit.Location;
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
import org.bukkit.inventory.EquipmentSlot;

public final class FurnitureListener implements Listener {
    private final CdrCarpenter plugin;
    private final FurnitureManager manager;
    private final FurnitureRegistry registry;
    private final SeatingManager seating;
    private final StorageManager storage;
    private final WorkbenchManager workbench;
    private final SawmillManager sawmill;

    public FurnitureListener(
            CdrCarpenter plugin,
            FurnitureManager manager,
            FurnitureRegistry registry,
            SeatingManager seating,
            StorageManager storage,
            WorkbenchManager workbench,
            SawmillManager sawmill
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
        this.seating = seating;
        this.storage = storage;
        this.workbench = workbench;
        this.sawmill = sawmill;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (manager.isConfiguredCollisionMaterial(clicked.getType())) {
            Interaction furniture = manager.findFurnitureAtCollisionBlock(clicked.getLocation());
            if (furniture != null) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    if (plugin.getConfig().getBoolean("placement.left-click-pickup", true)) {
                        pickupIfFree(event.getPlayer(), furniture);
                    }
                    return;
                }

                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    boolean requireSneak = plugin.getConfig().getBoolean("placement.pickup-requires-sneak", true);
                    if (!requireSneak || event.getPlayer().isSneaking()) {
                        pickupIfFree(event.getPlayer(), furniture);
                    } else if (!storage.open(event.getPlayer(), furniture)
                            && !workbench.open(event.getPlayer(), furniture)
                            && !sawmill.open(event.getPlayer(), furniture)) {
                        seating.sit(event.getPlayer(), furniture);
                    }
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
        if (manager.place(event.getPlayer(), definition, target.getLocation(), event.getHand())) {
            plugin.feedback().furniturePlaced(event.getPlayer(), target.getLocation().clone().add(0.5D, 0.15D, 0.5D));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Interaction interaction) || !manager.isFurnitureEntity(interaction)) {
            return;
        }

        boolean requireSneak = plugin.getConfig().getBoolean("placement.pickup-requires-sneak", true);
        if (!requireSneak || event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            pickupIfFree(event.getPlayer(), interaction);
            return;
        }

        if (storage.open(event.getPlayer(), interaction)) {
            event.setCancelled(true);
            return;
        }
        if (workbench.open(event.getPlayer(), interaction)) {
            event.setCancelled(true);
            return;
        }
        if (sawmill.open(event.getPlayer(), interaction)) {
            event.setCancelled(true);
            return;
        }
        if (seating.sit(event.getPlayer(), interaction)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureDamage(EntityDamageByEntityEvent event) {
        if (!manager.isFurnitureEntity(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player
                && plugin.getConfig().getBoolean("placement.left-click-pickup", true)
                && event.getEntity() instanceof Interaction interaction) {
            pickupIfFree(player, interaction);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCollisionBlockBreak(BlockBreakEvent event) {
        if (!manager.isConfiguredCollisionMaterial(event.getBlock().getType())) {
            return;
        }
        Interaction furniture = manager.findFurnitureAtCollisionBlock(event.getBlock().getLocation());
        if (furniture == null) {
            return;
        }
        event.setCancelled(true);
        pickupIfFree(event.getPlayer(), furniture);
    }

    private void pickupIfFree(Player player, Interaction interaction) {
        if (seating.isOccupied(interaction)) {
            player.sendMessage("§eSomeone is sitting on this furniture.");
            return;
        }

        String storageBlock = storage.pickupBlockReason(interaction);
        if (storageBlock != null) {
            player.sendMessage(storageBlock);
            return;
        }
        String workbenchBlock = workbench.pickupBlockReason(interaction);
        if (workbenchBlock != null) {
            player.sendMessage(workbenchBlock);
            return;
        }
        String sawmillBlock = sawmill.pickupBlockReason(interaction);
        if (sawmillBlock != null) {
            player.sendMessage(sawmillBlock);
            return;
        }

        Location effectLocation = interaction.getLocation().clone();
        storage.discardEmptyStorage(interaction);
        if (manager.pickup(player, interaction)) {
            plugin.feedback().furniturePickedUp(player, effectLocation);
        }
    }
}
