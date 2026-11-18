# HtzCosmetics

Free, client-side cosmetics for Minecraft (Fabric). Hats, halos and more, visible to every player who also
has the mod. It works on any server, including vanilla ones: the server does not need the mod.

## Features

- **Wardrobe** with 3D cards and a large preview you can rotate, zoom (mouse wheel) and move (right drag).
  Open it with **J**, `/htzcosmetics menu`, or the wardrobe button on the title and pause screens.
- **Animated cosmetics** (keyframe animations: idle, walking, sneaking).
- **Instant updates:** when you change your cosmetics, nearby players see the change right away.
- **Options:** hide other players' cosmetics, show your own in first person, and more.
- Works with **Sodium**, **Iris** (shaders), **Entity Model Features** and **Fresh Animations**.
- English and Spanish.

## Versions

| Minecraft | Fabric Loader | Fabric API |
|---|---|---|
| 1.21.8 | 0.16.13+ | 0.129.0+ |
| 1.21.10 | 0.17.0+ | 0.134.1+ |
| 1.21.11 | 0.17.3+ | 0.139.4+ |
| 26.1 – 26.1.2 | 0.18.4+ | 0.144.0+ |
| 26.2 | 0.18.4+ | 0.152.1+ |
| 26.3 | 0.19.3+ | 0.160.5+ |

Forge and NeoForge (1.20.1 – 1.21.1) are planned.

## Security

Cosmetics are **data only**: 3D models in our own JSON format and PNG textures. Nothing downloaded is ever
run as code. The list of cosmetics is signed (Ed25519) and every file is checked by its SHA-256 hash before
use; anything that fails a check is ignored. Network access is HTTPS-only, limited to two fixed addresses,
with size limits and timeouts. Details (in Spanish): [SEGURIDAD.md](SEGURIDAD.md).

## Privacy

See [PRIVACY.md](PRIVACY.md). In short: the server stores only your Minecraft UUID and the ids of the
cosmetics you wear. Your Minecraft login token never leaves your computer.
