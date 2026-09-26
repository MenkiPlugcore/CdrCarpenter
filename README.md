# CdrCarpenter

Free/open-source carpenter and furniture gameplay core for Paper 1.21.11.

## v0.1.1 — ItemsAdder Resource Integration

Current scope:

- Data-driven furniture registry (`furniture.yml`)
- Furniture items protected with PersistentDataContainer tags
- `/carpenter give <player> <id> [amount]`
- `/carpenter list`
- `/carpenter reload`
- Right-click block placement
- 90-degree rotation snapping based on player facing
- Persistent `ItemDisplay` visual entity
- Persistent `Interaction` entity for interaction hitbox
- Owner metadata and instance UUID pairing
- Sneak + right-click pickup
- Owner-only pickup with admin bypass
- Damage protection for tagged furniture entities
- Source `.bbmodel` archive
- ItemsAdder optional renderer bridge with vanilla fallback
- First runtime custom model: `cdrcarpenter:chair`
- GSit declared as a soft dependency; accurate seating is reserved for the seating phase

## Requirements

- Paper 1.21.11
- Java 21
- ItemsAdder: optional, required only for the bundled custom model visuals
- GSit: optional in v0.1.1

## ItemsAdder installation

Copy the bundled `contents/cdrcarpenter` directory into your ItemsAdder `contents` directory, then regenerate the ItemsAdder resource pack using your normal `/iazip` workflow.

Tests:

```text
/iaget cdrcarpenter:chair
/carpenter give <player> chair
```

CdrCarpenter intentionally controls furniture placement itself. The ItemsAdder item is used as the visual/custom-item source so ownership, persistence, rotation, future collision, workstations and GSit seating stay under CdrCarpenter.

If ItemsAdder is absent, or a mapped ItemsAdder item does not exist yet, CdrCarpenter falls back to the configured vanilla material/CustomModelData instead of disabling the plugin.

## Commands

```text
/carpenter list
/carpenter give <player> <chair|table|bookshelf|workbench|sawmill> [amount]
/carpenter reload
```

## Model status

- Chair: ItemsAdder runtime preview wired in v0.1.1
- Table: source archived, runtime export pending
- Bookshelf: source archived, runtime export pending
- Workbench: source archived, runtime export pending
- Sawmill: source archived, runtime export pending

The original Blockbench sources remain in `models/source/` for later scale, pivot, collision and seating calibration.

## Roadmap

- v0.1.1: ItemsAdder bridge + Chair model
- v0.1.x: Table, Bookshelf, Workbench and Sawmill model passes
- v0.1.x: collision calibration
- v0.1.x: GSit precision seat anchors
- v0.2.x: sawmill + carpenter workstation gameplay
- v0.3.x: blueprint learning/crafting system

## License

Plugin code: MIT. See `docs/MODEL_CREDITS.md` for third-party model licensing and attribution.
