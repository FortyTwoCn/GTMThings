package com.hepdd.gtmthings.client;

import com.gregtechceu.gtceu.client.renderer.AABBHighlightRenderer;

import net.minecraft.core.BlockPos;

public final class ClientHighlights {

    public static void highlight(BlockPos pos, long durationMillis) {
        AABBHighlightRenderer.INSTANCE.addHighlight(AABBHighlightRenderer.builder().aabb(pos)
                .colorARGB(0xFF3333FF).thickness(0.025f).durationMillis(durationMillis).phaseMillis(500).build());
    }
}
