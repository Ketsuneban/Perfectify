package dev.ketsuneban.yeta;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Yeta.MOD_ID)
public final class SnowFeatures {
    public static final int MAX_SNOW_LAYERS = 8;
    private static int ticks;

    private SnowFeatures() {}

    @SubscribeEvent
    public static void placeOnPlant(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Items.SNOW)) return;
        var state = event.getLevel().getBlockState(event.getPos());
        if (!isSoftPlant(state)) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getLevel().isClientSide()) return;
        ServerLevel level = (ServerLevel)event.getLevel();
        SnowStorage.of(level).cover(event.getPos(), state);
        level.setBlock(event.getPos(), Blocks.SNOW.defaultBlockState(), 3);
        if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
    }

    public static boolean isSoftPlant(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH)
            || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
            || state.is(Blocks.PINK_PETALS);
    }

    @SubscribeEvent
    public static void restoreCoveredPlant(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(Blocks.SNOW)) return;
        var plant = SnowStorage.of(level).take(event.getPos());
        if (plant == null) return;
        int layers = event.getState().getValue(SnowLayerBlock.LAYERS);
        event.setCanceled(true);
        level.setBlock(event.getPos(), plant, 3);
        net.minecraft.world.level.block.Block.popResource(level, event.getPos(),
            new net.minecraft.world.item.ItemStack(Items.SNOWBALL, Math.max(1, layers)));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (++ticks % 100 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.isRaining()) continue;
            for (var player : level.players()) {
                for (int trial = 0; trial < 3; trial++) {
                    if (level.random.nextInt(3) == 0) continue;
                    int x = player.getBlockX() + level.random.nextInt(41) - 20;
                    int z = player.getBlockZ() + level.random.nextInt(41) - 20;
                    BlockPos check = new BlockPos(x, player.getBlockY(), z);
                    if (!level.hasChunkAt(check)) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos sky = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(sky) || !level.canSeeSky(sky)) continue;
                    if (level.getBiome(sky).value().getPrecipitationAt(sky)
                            != Biome.Precipitation.SNOW) continue;
                    BlockPos surface = sky.below();
                    var old = level.getBlockState(surface);
                    if (old.is(Blocks.SNOW)) {
                        int layers = old.getValue(SnowLayerBlock.LAYERS);
                        if (layers < MAX_SNOW_LAYERS) {
                            level.setBlock(surface, old.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                        }
                    } else {
                        var overlay = level.getBlockState(sky);

                        boolean soft = overlay.isAir() || isSoftPlant(overlay);
                        var layer = Blocks.SNOW.defaultBlockState();
                        if (soft && (overlay.isAir() ? layer.canSurvive(level, sky) :
                                level.getBlockState(sky.below()).isFaceSturdy(level, sky.below(),
                                    net.minecraft.core.Direction.UP))) {
                            if (!overlay.isAir()) SnowStorage.of(level).cover(sky, overlay);
                            level.setBlock(sky, layer, 3);
                        }
                    }
                }
            }
        }
    }
}
