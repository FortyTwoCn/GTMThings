package com.hepdd.gtmthings.api.pattern;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.api.misc.Hatch;
import com.hepdd.gtmthings.common.item.AdvancedTerminalBehavior.AutoBuildSetting;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;

/** Uses GTCEu 8's public structure helpers for counts, repetition, rotations and outward hatch faces. */
public final class AdvancedBlockPattern {

    private final IBlockPattern pattern;

    public AdvancedBlockPattern(IBlockPattern pattern) {
        this.pattern = pattern;
    }

    public boolean autoBuild(Player player, MultiblockControllerMachine controller, AutoBuildSetting options) {
        var level = player.level();
        var front = controller.getFrontFacing();
        var up = controller.getUpwardsFacing();
        boolean flip = controller.isFlipped();
        AbstractStructureHelper helper;
        if (pattern instanceof BlockPattern blockPattern) {
            var repeats = new Int2IntOpenHashMap();
            var slices = blockPattern.getSlices();
            for (int i = 0; i < slices.length; i++) repeats.put(i, Math.clamp(options.repeatCount(), slices[i].getMinRepeats(), slices[i].getMaxRepeats()));
            helper = AbstractStructureHelper.blockPattern(repeats);
            blockPattern.getPredicates().values().forEach(p -> configure(helper, p, options, player, Set.of()));
        } else if (pattern instanceof ExpandablePattern expandable && expandable.getBoundsConstraints() != null) {
            var bounds = new IntArrayList();
            for (var range : expandable.getBoundsConstraints().apply()) bounds.add(Math.clamp(options.repeatCount(), range.leftInt(), range.rightInt()));
            if (bounds.size() != 6) return false;
            helper = AbstractStructureHelper.expandable(bounds);
        } else {
            player.displayClientMessage(Component.translatable("gui.gtmthings.terminal.unsupported_pattern"), true);
            return false;
        }
        Map<BlockPos, BlockInfo> planned = new LinkedHashMap<>();
        Map<BlockPos, MultiPredicate> predicates = predicateMap(pattern, options, front, up, flip);
        try {
            helper.populate(planned, pattern, null, front, up, flip);
        } catch (IllegalStateException exception) {
            GTMThings.LOGGER.warn("Cannot populate terminal structure {}: {}", controller.getDefinition().getId(), exception.getMessage());
            player.displayClientMessage(Component.translatable("gui.gtmthings.terminal.invalid_pattern"), true);
            return false;
        }
        // BlockPattern helpers use a nonnegative bounding box. Anchor it to the actual controller character.
        BlockPos origin = controller.getBlockPos();
        if (pattern instanceof BlockPattern blockPattern) {
            BlockPos anchor = planned.keySet().stream().filter(p -> predicates.get(p).isController()).findFirst().orElse(null);
            if (anchor != null) origin = origin.subtract(anchor);
            else {
                BlockPos.MutableBlockPos start = origin.mutable();
                pattern.getOffset().apply(start, front, up, flip);
                int[] size = blockPattern.getDimensions().clone();
                size[0] = Arrays.stream(blockPattern.getSlices()).mapToInt(s -> Math.clamp(options.repeatCount(), s.getMinRepeats(), s.getMaxRepeats())).sum();
                for (int i = 0; i < 3; i++) {
                    var dir = blockPattern.getDirections()[i].getRelativeFacing(front, up, flip);
                    if (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) start.move(dir, size[i] - 1);
                }
                origin = start.immutable();
            }
        } else {
            var start = origin.mutable();
            pattern.getOffset().apply(start, front, up, flip);
            origin = start.immutable();
            // Expandable predicates are generated per coordinate rather than stored on the pattern.
            for (var pos : planned.keySet()) configure(helper, predicates.computeIfAbsent(pos, p -> helper.getPredicateFromPos(pattern, p, front, up, flip)), options, player, Set.of());
        }
        var existing = new Long2ObjectOpenHashMap<BlockInfo>();
        for (var pos : planned.keySet()) {
            BlockPos worldPos = pos.offset(origin);
            if (!level.hasChunkAt(worldPos)) continue;
            Block old = level.getBlockState(worldPos).getBlock();
            if (old instanceof CoilBlock && options.replaceCoils()) continue;
            var predicate = predicates.computeIfAbsent(pos, p -> helper.getPredicateFromPos(pattern, p, front, up, flip));
            findCandidate(predicate, old).ifPresent(info -> existing.put(pos.asLong(), info));
        }
        // Existing parts count towards min/max constraints; never blindly add a second required hatch.
        if (!existing.isEmpty() || pattern instanceof ExpandablePattern) {
            Set<Block> existingBlocks = new HashSet<>();
            existing.values().forEach(info -> existingBlocks.add(info.getBlockState().getBlock()));
            new HashSet<>(predicates.values()).forEach(p -> configure(helper, p, options, player, existingBlocks));
            planned.clear();
            try {
                helper.populate(planned, pattern, existing, front, up, flip);
            } catch (IllegalStateException exception) {
                player.displayClientMessage(Component.translatable("gui.gtmthings.terminal.invalid_pattern"), true);
                return false;
            }
        }
        boolean changed = false;
        for (var entry : planned.entrySet()) {
            var desired = entry.getValue();
            BlockPos pos = entry.getKey().offset(origin);
            if (desired.isAir() || pos.equals(controller.getBlockPos()) || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)) continue;
            var old = level.getBlockState(pos);
            if (old.getBlock() == desired.getBlockState().getBlock()) continue;
            boolean replaceCoil = options.replaceCoils() && old.getBlock() instanceof CoilBlock && desired.getBlockState().getBlock() instanceof CoilBlock;
            if (!old.canBeReplaced() && !replaceCoil) continue;
            if (options.noHatches() && Hatch.isHatch(desired.getBlockState().getBlock())) continue;
            ItemStack candidate = desired.getItemStackForm();
            Source source = findSource(player, candidate, options.useAE());
            if (source == null) continue;
            ItemStack reserved = source.take();
            if (reserved.isEmpty() || !(reserved.getItem() instanceof BlockItem item)) continue;
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            boolean placed = false;
            try {
                if (replaceCoil) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, reserved.copy(),
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                placed = item.place(context).consumesAction() && level.getBlockState(pos).getBlock() == desired.getBlockState().getBlock();
                if (placed) {
                    // Native helper decides rotations; BlockItem.place preserves components and calls setPlacedBy.
                    level.setBlock(pos, desired.getBlockState(), 3);
                    placed = !EventHooks.onBlockPlace(player, snapshot, Direction.UP);
                }
            } catch (RuntimeException exception) {
                GTMThings.LOGGER.warn("Terminal placement failed at {}", pos, exception);
            } finally {
                if (!placed) {
                    snapshot.restore();
                    source.refund(reserved);
                }
            }
            if (placed) {
                changed = true;
                if (replaceCoil && !player.isCreative()) ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(old.getBlock()));
            }
        }
        return changed;
    }

    public static Map<BlockPos, MultiPredicate> predicateMap(IBlockPattern pattern, AutoBuildSetting options, Direction front, Direction up, boolean flip) {
        Map<BlockPos, MultiPredicate> result = new HashMap<>();
        if (!(pattern instanceof BlockPattern bp)) return result;
        int[] size = bp.getDimensions().clone();
        size[0] = Arrays.stream(bp.getSlices()).mapToInt(s -> Math.clamp(options.repeatCount(), s.getMinRepeats(), s.getMaxRepeats())).sum();
        Direction[] dirs = Arrays.stream(bp.getDirections()).map(d -> d.getRelativeFacing(front, up, flip)).toArray(Direction[]::new);
        BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos();
        for (int axis = 0; axis < 3; axis++) if (dirs[axis].getAxisDirection() == Direction.AxisDirection.NEGATIVE) min.move(dirs[axis], size[axis] - 1);
        int sliceIndex = 0;
        for (var slice : bp.getSlices()) for (int repeat = 0; repeat < Math.clamp(options.repeatCount(), slice.getMinRepeats(), slice.getMaxRepeats()); repeat++, sliceIndex++) {
            for (int line = 0; line < size[1]; line++) for (int chr = 0; chr < size[2]; chr++) {
                BlockPos pos = BlockPos.ZERO.relative(dirs[0], sliceIndex).relative(dirs[1], line).relative(dirs[2], chr).subtract(min);
                result.put(pos, bp.getPredicates().get(slice.charAt(line, chr)));
            }
        }
        return result;
    }

    private static Optional<BlockInfo> findCandidate(MultiPredicate predicate, Block block) {
        for (var base : predicate.predicates()) for (var info : base.getCandidates()) if (info.getBlockState().getBlock() == block) return Optional.of(info);
        for (var child : predicate.children()) {
            var found = findCandidate(child, block);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static void configure(AbstractStructureHelper helper, MultiPredicate predicate, AutoBuildSetting options, Player player, Set<Block> existing) {
        boolean hasCasing = predicate.predicates().stream().flatMap(p -> p.getCandidates().stream()).anyMatch(i -> i.nonAir() && !Hatch.isHatch(i.getBlockState().getBlock()));
        configure(helper, predicate, options, player, hasCasing, existing);
    }

    private static void configure(AbstractStructureHelper helper, MultiPredicate predicate, AutoBuildSetting options, Player player, boolean hasCasing, Set<Block> existing) {
        for (var base : predicate.predicates()) {
            helper.getMinMaxPreferences().remove(predicate, base);
            List<BlockInfo> candidates = base.getCandidates();
            boolean coils = candidates.stream().anyMatch(i -> i.getBlockState().getBlock() instanceof CoilBlock);
            if (coils) candidates = candidates.stream().filter(i -> i.getBlockState().getBlock() instanceof CoilBlock coil && (options.coilTier() == 0 || coil.coilType.getTier() + 1 == options.coilTier())).toList();
            if (options.noHatches() && hasCasing && !candidates.isEmpty() && candidates.stream().allMatch(i -> Hatch.isHatch(i.getBlockState().getBlock()))) {
                helper.getMinMaxPreferences().put(predicate, base, it.unimi.dsi.fastutil.ints.IntIntPair.of(0, 0));
                continue;
            }
            BlockInfo choice = candidates.stream().filter(i -> findSource(player, i.getItemStackForm(), options.useAE()) != null).findFirst().orElse(candidates.isEmpty() ? null : candidates.getFirst());
            if (choice != null) helper.getBlockPreferences().put(predicate, base, choice);
        }
        for (var child : predicate.children()) configure(helper, child, options, player, hasCasing, existing);
        if (predicate.isXor()) {
            // GT8's preview helper otherwise tries to satisfy every exclusive branch's minimum.
            // Prefer the branch already in the world, then one the player can actually supply.
            List<MultiPredicate> branches = new ArrayList<>(predicate.children());
            for (var base : predicate.predicates()) branches.add(MultiPredicate.of(base.copy()));
            MultiPredicate selected = branches.stream().filter(p -> p.expand().stream().flatMap(b -> b.getCandidates().stream()).anyMatch(i -> existing.contains(i.getBlockState().getBlock()))).findFirst()
                    .orElseGet(() -> branches.stream().filter(p -> p.expand().stream().flatMap(b -> b.getCandidates().stream()).anyMatch(i -> findSource(player, i.getItemStackForm(), options.useAE()) != null)).findFirst().orElse(branches.isEmpty() ? null : branches.getFirst()));
            int baseIndex = predicate.children().size();
            for (var base : predicate.predicates()) if (branches.get(baseIndex++) != selected)
                helper.getMinMaxPreferences().put(predicate, base, it.unimi.dsi.fastutil.ints.IntIntPair.of(0, 0));
            for (var child : predicate.children()) if (child != selected) disableBranch(helper, child);
        }
    }

    private static void disableBranch(AbstractStructureHelper helper, MultiPredicate predicate) {
        for (var base : predicate.predicates()) helper.getMinMaxPreferences().put(predicate, base, it.unimi.dsi.fastutil.ints.IntIntPair.of(0, 0));
        for (var child : predicate.children()) disableBranch(helper, child);
    }

    private interface Source {

        ItemStack take();

        void refund(ItemStack stack);
    }

    private static Source findSource(Player player, ItemStack candidate, boolean useAE) {
        if (candidate.isEmpty() || !(candidate.getItem() instanceof BlockItem)) return null;
        if (player.isCreative()) return new Source() {

            public ItemStack take() {
                return candidate.copyWithCount(1);
            }

            public void refund(ItemStack ignored) {}
        };
        return search(new InvWrapper(player.getInventory()), player, candidate, useAE,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static Source search(IItemHandler inventory, Player player, ItemStack candidate, boolean useAE, Set<IItemHandler> visited, int depth) {
        if (depth > 16 || !visited.add(inventory)) return null;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            final int index = slot;
            if (ItemStack.isSameItemSameComponents(stack, candidate) && inventory.extractItem(slot, 1, true).getCount() == 1) return new Source() {

                public ItemStack take() {
                    return inventory.extractItem(index, 1, false);
                }

                public void refund(ItemStack reserved) {
                    var remainder = inventory.insertItem(index, reserved, false);
                    if (!remainder.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, remainder);
                }
            };
            IItemHandler nested = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (nested != null) {
                var result = search(nested, player, candidate, useAE, visited, depth + 1);
                if (result != null) return result;
            }
            if (useAE && stack.getItem() instanceof WirelessTerminalItem terminal) {
                var grid = terminal.getLinkedGrid(stack, player.level(), message -> {});
                if (grid == null) continue;
                MEStorage storage = grid.getStorageService().getInventory();
                var key = AEItemKey.of(candidate);
                var action = IActionSource.ofPlayer(player);
                if (key != null && storage.extract(key, 1, Actionable.SIMULATE, action) == 1) return new Source() {

                    public ItemStack take() {
                        return storage.extract(key, 1, Actionable.MODULATE, action) == 1 ? key.toStack() : ItemStack.EMPTY;
                    }

                    public void refund(ItemStack reserved) {
                        if (storage.insert(key, 1, Actionable.MODULATE, action) != 1) ItemHandlerHelper.giveItemToPlayer(player, reserved);
                    }
                };
            }
        }
        return null;
    }
}
