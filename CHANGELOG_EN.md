# Yeta Changelog

Every project change is recorded here, including release preparation.

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
