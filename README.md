# CdrCarpenter

Free/open-source carpenter and furniture gameplay core for Paper 1.21.11.

## v0.1.0 — Furniture Core

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
- GSit declared as a soft dependency; accurate seating is intentionally reserved for a later phase

## Requirements

- Paper 1.21.11
- Java 21
- GSit is optional in v0.1.0

## Important v0.1.0 note

The original Blockbench models are archived in `models/source/`, but runtime resource-pack exports are not generated yet. Until the model normalization/export phase is completed, furniture uses the vanilla material configured in `furniture.yml` as its display item.

## Commands

```text
/carpenter list
/carpenter give <player> <chair|table|bookshelf|workbench|sawmill> [amount]
/carpenter reload
```

## Roadmap

- v0.1.x: model/resource-pack wiring and placement calibration
- v0.2.x: sawmill + carpenter workstation gameplay
- v0.3.x: blueprint learning/crafting system
- later: GSit seat anchors and per-model precision calibration

## License

Plugin code: MIT. See `docs/MODEL_CREDITS.md` for third-party model licensing and attribution.
