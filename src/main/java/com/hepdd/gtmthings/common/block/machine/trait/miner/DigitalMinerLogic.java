package com.hepdd.gtmthings.common.block.machine.trait.miner;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.filter.Filter;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import com.hepdd.gtmthings.common.block.machine.electric.DigitalMiner;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;

import java.util.LinkedList;

/** Bounded world scan with a persisted queue; no recipe lookup is needed to mine a world block. */
public class DigitalMinerLogic extends RecipeLogic {

    private static final int SCAN_LIMIT = 4096, QUEUE_LIMIT = 256;
    @Getter
    private final int speed;
    @Getter
    @SaveField
    private int silk;
    @Getter
    @SaveField
    private int maximumRadius;
    @Getter
    @SaveField
    private int minHeight, maxHeight;
    @Getter
    @SaveField
    private int currentRadius;
    @Getter
    @SaveField
    private boolean isDone;
    @Getter
    private boolean isInventoryFull;
    @Getter
    @SaveField
    protected int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
    @Getter
    @SaveField
    protected int startX = Integer.MAX_VALUE, startY = Integer.MAX_VALUE, startZ = Integer.MAX_VALUE;
    @Getter
    @SaveField
    protected int mineX = Integer.MAX_VALUE, mineY = Integer.MAX_VALUE, mineZ = Integer.MAX_VALUE;
    @SaveField
    private final LinkedList<BlockPos> blocksToMine = new LinkedList<>();
    private Filter<ItemStack> filter;
    private ItemStack pickaxeTool;
    private boolean waitingForChunk;
    private boolean scanFinished;
    private BlockState replacement;

    public DigitalMinerLogic(int tier) {
        super();
        speed = Math.max(1, 40 / (1 << tier));
        maximumRadius = Math.min(32, 8 << tier);
        currentRadius = maximumRadius;
        minHeight = 0;
        maxHeight = 64;
        pickaxeTool = GTMaterialItems.TOOL_ITEMS.get(GTMaterials.Neutronium, GTToolType.PICKAXE).get().get();
    }

    private DigitalMiner miner() {
        return (DigitalMiner) getMachine();
    }

    public int getOreAmount() {
        return blocksToMine.size();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        configure(miner().getMinerRadius(), miner().getMinHeight(), miner().getMaxHeight(), miner().getSilkLevel(), miner().getItemFilter());
        if (getLevel() instanceof ServerLevel level) {
            try {
                replacement = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK),
                        ConfigHolder.INSTANCE.machines.replaceMinedBlocksWith, false).blockState();
            } catch (CommandSyntaxException exception) {
                GTCEu.LOGGER.error("Invalid replaceMinedBlocksWith: {}", ConfigHolder.INSTANCE.machines.replaceMinedBlocksWith);
                replacement = Blocks.COBBLESTONE.defaultBlockState();
            }
        }
    }

    public void configure(int radius, int min, int max, int silk, Filter<ItemStack> filter) {
        maximumRadius = radius;
        minHeight = Math.min(min, max);
        maxHeight = Math.max(min, max);
        this.silk = silk;
        this.filter = filter;
        if (currentRadius <= 0 || currentRadius > maximumRadius) currentRadius = maximumRadius;
        if (getLevel() != null) {
            var registry = getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            pickaxeTool.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            pickaxeTool.enchant(registry.getHolderOrThrow(silk == 1 ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE), 1);
        }
    }

    public void resetRecipeLogic(int radius, int min, int max, int silk, Filter<ItemStack> filter) {
        configure(radius, min, max, silk, filter);
        currentRadius = maximumRadius;
        resetArea(false);
        super.resetRecipeLogic();
        setWorkingEnabled(false);
    }

    public void setCurrentRadius(int radius) {
        currentRadius = Math.clamp(radius, 1, maximumRadius);
        markAsChanged();
    }

    public void resetArea(boolean checkToMine) {
        blocksToMine.clear();
        isDone = false;
        isInventoryFull = false;
        waitingForChunk = false;
        scanFinished = false;
        initPos(getBlockPos(), currentRadius);
        if (checkToMine && getLevel() instanceof ServerLevel level) scan(level);
        markAsChanged();
        updateTickSubscription();
    }

    public void initPos(BlockPos pos, int radius) {
        startX = pos.getX() - radius;
        startZ = pos.getZ() - radius;
        startY = maxHeight;
        x = startX;
        z = startZ;
        y = maxHeight;
        mineX = startX;
        mineZ = startZ;
        mineY = maxHeight;
    }

    @Override
    public void updateTickSubscription() {
        if (isDone || isSuspend()) {
            if (subscription != null) {
                subscription.unsubscribe();
                subscription = null;
            }
        } else super.updateTickSubscription();
    }

    @Override
    public void serverTick() {
        if (isSuspend() || isDone || !(getLevel() instanceof ServerLevel level)) {
            updateTickSubscription();
            return;
        }
        if (!miner().drainInput(true)) {
            setWaiting(Component.translatable("gtceu.multiblock.large_miner.needspower"));
            return;
        }
        if (x == Integer.MAX_VALUE && y == Integer.MAX_VALUE && z == Integer.MAX_VALUE) initPos(getBlockPos(), currentRadius);
        scanFinished = y < minHeight;
        if (blocksToMine.isEmpty() && !scanFinished) scan(level);
        if (waitingForChunk && !blocksToMine.isEmpty() && level.hasChunkAt(blocksToMine.getFirst())) waitingForChunk = false;
        if (waitingForChunk) {
            setWaiting(Component.translatable("gui.gtmthings.digital_miner.chunk_unloaded"));
            return;
        }
        if (!isInventoryFull) miner().drainInput(false);
        setStatus(Status.WORKING);
        if (getMachine().getOffsetTimer() % speed == 0 && !blocksToMine.isEmpty()) {
            BlockPos pos = blocksToMine.getFirst();
            if (!level.hasChunkAt(pos)) {
                waitingForChunk = true;
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!isMineable(level, pos, state)) {
                blocksToMine.removeFirst();
                isInventoryFull = false;
            } else {
                var loot = new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE, state)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.TOOL, pickaxeTool);
                var drops = state.getDrops(loot);
                if (GTTransferUtils.addItemsToItemHandler(miner().exportItems.storage, true, drops)) {
                    GTTransferUtils.addItemsToItemHandler(miner().exportItems.storage, false, drops);
                    level.setBlock(pos, replacement == null ? Blocks.COBBLESTONE.defaultBlockState() : replacement, 3);
                    mineX = pos.getX();
                    mineY = pos.getY();
                    mineZ = pos.getZ();
                    blocksToMine.removeFirst();
                    isInventoryFull = false;
                } else {
                    isInventoryFull = true;
                    setWaiting(Component.translatable("gtceu.multiblock.large_miner.invfull"));
                }
            }
        }
        if (scanFinished && blocksToMine.isEmpty()) {
            isDone = true;
            setStatus(Status.IDLE);
            updateTickSubscription();
        }
        markAsChanged();
    }

    private boolean isMineable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(Tags.Blocks.ORES) && state.getDestroySpeed(level, pos) >= 0 && !state.hasBlockEntity() && (filter == null || filter.test(state.getBlock().asItem().getDefaultInstance()));
    }

    private void scan(ServerLevel level) {
        waitingForChunk = false;
        long deadline = System.nanoTime() + 2_000_000L;
        int visited = 0;
        while (y >= minHeight && visited++ < SCAN_LIMIT && blocksToMine.size() < QUEUE_LIMIT && System.nanoTime() < deadline) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                waitingForChunk = blocksToMine.isEmpty();
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (isMineable(level, pos, state)) blocksToMine.addLast(pos);
            if (++x > startX + currentRadius * 2) {
                x = startX;
                if (++z > startZ + currentRadius * 2) {
                    z = startZ;
                    --y;
                }
            }
        }
        scanFinished = y < minHeight;
        markAsChanged();
    }
}
