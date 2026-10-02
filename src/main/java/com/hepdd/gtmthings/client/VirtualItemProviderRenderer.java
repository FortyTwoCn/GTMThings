package com.hepdd.gtmthings.client;

import com.gregtechceu.gtceu.client.renderer.ItemWithBERModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.hepdd.gtmthings.common.item.VirtualItemProviderBehavior;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;

public final class VirtualItemProviderRenderer extends ItemWithBERModelRenderer {

    public static final VirtualItemProviderRenderer INSTANCE = new VirtualItemProviderRenderer();

    private VirtualItemProviderRenderer() {}

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context,
                             @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
                             int light, int overlay) {
        ItemStack selected = VirtualItemProviderBehavior.getVirtualItem(stack);
        if (!selected.isEmpty()) {
            var minecraft = Minecraft.getInstance();
            var model = itemRenderer.getModel(selected, minecraft.level, minecraft.player, 0);
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            itemRenderer.render(selected, context, false, pose, buffers, light, overlay, model);
            pose.popPose();
        }
        if (selected.isEmpty() || context == ItemDisplayContext.GUI) {
            // The GT native wrapper retains the generated model for the provider's overlay.
            super.renderByItem(stack, context, pose, buffers, light, overlay);
        }
    }
}
