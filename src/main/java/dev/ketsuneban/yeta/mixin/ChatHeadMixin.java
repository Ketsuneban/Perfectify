package dev.ketsuneban.yeta.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatComponent.class)
public abstract class ChatHeadMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I",
        ordinal = 0), require = 0)
    private int yeta$drawPlayerLine(GuiGraphics graphics, Font font,
                                    FormattedCharSequence content, int x, int y, int color) {
        PlayerInfo sender = findSender(content);
        if (sender == null) return graphics.drawString(font, content, x, y, color);
        graphics.fill(x, y - 1, x + 10, y + 9, 0xAA101A2A);
        graphics.blit(sender.getSkin().texture(), x + 1, y, 8, 8, 8, 8, 64, 64);
        graphics.blit(sender.getSkin().texture(), x + 1, y, 40, 8, 8, 8, 64, 64);
        return graphics.drawString(font, content, x + 11, y, color);
    }

    private PlayerInfo findSender(FormattedCharSequence content) {
        if (minecraft.getConnection() == null) return null;
        StringBuilder plain = new StringBuilder();
        content.accept((index, style, codepoint) -> {
            plain.appendCodePoint(codepoint);
            return true;
        });
        String message = plain.toString();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (message.startsWith("<" + name + ">") || message.startsWith(name + ":")
                    || message.startsWith(name + " »") || message.startsWith(name + " >")) {
                return info;
            }
        }
        return null;
    }
}
