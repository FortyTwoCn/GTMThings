package com.hepdd.gtmthings.api.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.function.Predicate;

import javax.annotation.Nullable;

public class UnlimitItemTransferHelper {

    public static void exportToTarget(IItemHandler source, int maxAmount, Predicate<ItemStack> predicate, Level level, BlockPos pos, @Nullable Direction direction) {
        if (maxAmount <= 0 || !level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos).hasBlockEntity()) {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                var target = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
                if (target != null) {
                    for (int srcIndex = 0; srcIndex < source.getSlots(); srcIndex++) {
                        while (true) {
                            ItemStack sourceStack = source.extractItem(srcIndex, Integer.MAX_VALUE, true);
                            if (sourceStack.isEmpty() || !predicate.test(sourceStack)) {
                                break;
                            }
                            ItemStack remainder = insertItem(target, sourceStack, true);
                            int amountToInsert = sourceStack.getCount() - remainder.getCount();
                            if (amountToInsert > 0) {
                                sourceStack = source.extractItem(srcIndex, Math.min(maxAmount, amountToInsert), false);
                                ItemStack rejected = insertItem(target, sourceStack, false);
                                int moved = sourceStack.getCount() - rejected.getCount();
                                if (!rejected.isEmpty()) {
                                    if (source instanceof net.neoforged.neoforge.items.IItemHandlerModifiable modifiable) {
                                        ItemStack current = source.getStackInSlot(srcIndex);
                                        if (current.isEmpty()) modifiable.setStackInSlot(srcIndex, rejected);
                                        else if (ItemStack.isSameItemSameComponents(current, rejected) && (long) current.getCount() + rejected.getCount() <= Integer.MAX_VALUE)
                                            modifiable.setStackInSlot(srcIndex, current.copyWithCount(current.getCount() + rejected.getCount()));
                                        else throw new IllegalStateException("Source changed during wireless item rollback");
                                    } else {
                                        rejected = source.insertItem(srcIndex, rejected, false);
                                        if (!rejected.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, pos, rejected);
                                    }
                                }
                                maxAmount -= moved;
                                if (moved <= 0) break;
                                if (maxAmount <= 0) return;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
                if (handler.getStackInSlot(slot).isEmpty()) stack = handler.insertItem(slot, stack, simulate);
            }
            return stack;
        }

        IntList emptySlots = new IntArrayList();
        int slots = handler.getSlots();

        for (int i = 0; i < slots; i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                emptySlots.add(i);
            }
            if (ItemStack.isSameItemSameComponents(stack, slotStack)) {
                stack = handler.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (int slot : emptySlots) {
            stack = handler.insertItem(slot, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }
}
