package dev.ketsuneban.yeta;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Yeta.MOD_ID, value = Dist.CLIENT)
public final class YetaClient {
    private static final long MAX_GAP_NANOS = 2_000_000_000L;
    private static final int[] SECRET_CODE = {
        GLFW.GLFW_KEY_KP_9,
        GLFW.GLFW_KEY_KP_9,
        GLFW.GLFW_KEY_KP_3
    };

    private static int progress = 0;
    private static long lastPressNanos = 0L;
    private static float snowWhiteness;
    private static int foliageRefreshTicks;
    private static ClientLevel lastLevel;

    private YetaClient() { }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {

        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            progress = 0;
            return;
        }

        long now = System.nanoTime();
        if (progress > 0 && now - lastPressNanos > MAX_GAP_NANOS) {
            progress = 0;
        }

        int key = event.getKey();
        if (key == SECRET_CODE[progress]) {
            progress++;
            lastPressNanos = now;
            if (progress == SECRET_CODE.length) {
                progress = 0;
                showSecretTitle(minecraft);
            }
        } else {

            progress = (key == SECRET_CODE[0]) ? 1 : 0;
            lastPressNanos = progress == 1 ? now : 0L;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            ClientCauldronColors.clear();
        }
        if (minecraft.level == null || minecraft.player == null) {
            snowWhiteness = 0.0F;
            return;
        }
        var pos = minecraft.player.blockPosition();
        boolean snowing = minecraft.level.isRaining()
            && minecraft.level.canSeeSky(pos.above())
            && minecraft.level.getBiome(pos).value().getPrecipitationAt(pos)
                == net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
        float target = snowing ? 1.0F : 0.0F;
        float previous = snowWhiteness;
        snowWhiteness += Math.copySign(Math.min(Math.abs(target - snowWhiteness), 0.0025F), target - snowWhiteness);
        if (Math.abs(previous - snowWhiteness) > 0.0001F && ++foliageRefreshTicks >= 20) {
            foliageRefreshTicks = 0;
            minecraft.levelRenderer.allChanged();
        }
    }

    public static float snowWhiteness() {
        return snowWhiteness;
    }

    private static void showSecretTitle(Minecraft minecraft) {

        minecraft.gui.setTimes(10, 70, 20);
        minecraft.gui.setSubtitle(
            Component.literal("NUMPAD 993 ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("• EVENT RECEIVED")
                    .withStyle(ChatFormatting.GRAY))
        );
        minecraft.gui.setTitle(
            Component.literal("SYSTEM EVENT")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
        );
    }
}
