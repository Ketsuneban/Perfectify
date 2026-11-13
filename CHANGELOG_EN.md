# Yeta Changelog

Every project change is recorded here, including release preparation.

## 0.2.0 — November 13, 2026

- Extended beacon beam visibility to 1024 blocks.
- Leaves now whiten gradually during snowfall and fade back after it ends.
- Added four chainmail crafting recipes using chains.
- Brought Wither difficulty closer to Bedrock with 300/450/600 health, stronger damage and a second-phase Wither Skeleton wave.
- Ender Dragon damage now scales by difficulty closer to Bedrock Edition.
- Zombified Piglins alert nearby allies even when one is killed instantly.
- Horses can board boats automatically.
- Block-breaking progress survives switching between suitable tools.
- Door and bed outlines include both halves.
- Pistons can move inventories while preserving their contents.
- Levers and buttons attached to pistons no longer break on activation.
- Fixed armor-stand pose cycling and full off-hand item insertion/removal.
- Potion and dyed-water cauldron colors now sync to clients; witch-hut potion generation is more reliable.
- Chat heads include the skin overlay, and indentation applies only to player chat without affecting the input field.
- Snow accumulates to eight layers, merges after falling and can cover grass, flowers and other soft plants.
- Iron Golems reliably perform the poppy gesture near child villagers.
- Kept the softer map palette and ported current map-item, player-marker and locator-bar textures.
- Fixed sheared sheep eye whites being tinted.
- Split the website into Home, Content, Updates, Source code and Information pages and corrected its labels and metadata.

## 0.1.1 — November 13, 2026

- Fixed all nine boat recipes: Minecraft 1.21.1 requires ingredient objects rather than string entries.
- CI now rejects JARs when RecipeManager reports recipe parsing failures.
- The initial 0.1.0 alpha had malformed recipes and is superseded by this release.

## 0.1.0 — November 13, 2026

- Created the new public Yeta repository and migrated sources from Perfectify.
- Renamed the mod ID, Java packages, localization and metadata.
- Added wooden shovel requirements to boat recipes.
- Added armor-stand arms, offhand slots, pose switching and 13 poses from official Bedrock samples.
- Added potion cauldrons, bottle filling, tipped arrows, water dyeing and armor dyeing.
- Added weighted witch-hut potion selection and reduced potion contact effects.
- Added capped uneven snow accumulation, falling layers and vegetation cover.
- Adjusted skeleton, creeper and zombie movement and iron golem flower interactions.
- Adjusted shield animation, map colors, sheep tinting, chat formatting and player portraits.
- Added bilingual notes, project website and release automation.

### Interaction fixes
- Empty buckets cannot extract plain water from potion-filled cauldrons.
- Water bottles now drain dyed cauldrons correctly; dye state resets when emptied.
- Replacing dyed water via a bucket clears the stored color.
- Fixed tipped arrow fluid cost: 1–16 arrows use one sixth of a sufficiently filled cauldron (or all remaining 1–3 sixths); 17–32 arrows use four sixths and 33–64 arrows use six sixths.
- Water and lava buckets now defer to vanilla bucket interactions when replacing potion cauldrons.
- Mixing plain water into a potion cauldron now consumes the water bottle and clears the contents.

### Known limitations

Full Bedrock parity and compatibility of all client Mixins require in-game validation. Certain rendering behavior remains experimental.
