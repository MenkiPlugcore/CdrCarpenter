# CdrCarpenter Resource Pack

The v0.1.0 repository intentionally keeps the original `.bbmodel` sources separate from exported runtime assets.

Runtime resource-pack assets will be generated after model scale, pivot, display transform, and texture paths are normalized. Furniture IDs reserved by the plugin:

| Furniture | ID | CustomModelData |
|---|---|---:|
| Chair | `chair` | 1001 |
| Table | `table` | 1002 |
| Bookshelf | `bookshelf` | 1003 |
| Carpenter Workbench | `workbench` | 1004 |
| Table Saw / Sawmill | `sawmill` | 1005 |

Until the pack is exported, the plugin uses the configured vanilla item as a visual placeholder.
