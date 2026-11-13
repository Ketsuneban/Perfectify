package dev.ketsuneban.yeta;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class YetaNetwork {
    private YetaNetwork() {}

    public record CauldronColorPayload(BlockPos pos, int color) implements CustomPacketPayload {
        public static final Type<CauldronColorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Yeta.MOD_ID, "cauldron_color"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CauldronColorPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, CauldronColorPayload::pos,
                ByteBufCodecs.INT, CauldronColorPayload::color, CauldronColorPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @EventBusSubscriber(modid = Yeta.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(CauldronColorPayload.TYPE,
                CauldronColorPayload.STREAM_CODEC,
                (payload, context) -> ClientCauldronColors.update(payload.pos(), payload.color()));
        }
    }

    public static void send(ServerLevel level, BlockPos pos, int color) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
            new CauldronColorPayload(pos, color));
    }

    @EventBusSubscriber(modid = Yeta.MOD_ID)
    public static final class PlayerSync {
        private PlayerSync() {}

        @SubscribeEvent
        public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) return;
            for (var entry : CauldronStorage.of(level).entries().entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                var contents = entry.getValue();
                int color = contents.isPotion()
                    ? CauldronFeatures.colorOf(contents) : contents.color();
                PacketDistributor.sendToPlayer(player, new CauldronColorPayload(pos, color));
            }
        }
    }
}
