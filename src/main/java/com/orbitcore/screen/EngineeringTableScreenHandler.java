package com.orbitcore.screen;

import com.orbitcore.recipe.EngineeringRecipe;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * Интерфейс верстака инженера. Устроен как обычный верстак, но ищет рецепты
 * только типа {@code orbitcore:engineering}.
 */
public class EngineeringTableScreenHandler extends ScreenHandler {
    public static final int RESULT = 0;
    private static final int GRID_END = 10;
    private static final int INVENTORY_END = 37;
    private static final int HOTBAR_END = 46;

    private final RecipeInputInventory input = new CraftingInventory(this, 3, 3);
    private final CraftingResultInventory result = new CraftingResultInventory();
    private final ScreenHandlerContext context;
    private final PlayerEntity player;

    public EngineeringTableScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public EngineeringTableScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(ModScreenHandlers.ENGINEERING_TABLE, syncId);
        this.context = context;
        this.player = playerInventory.player;
        addSlot(new ResultSlot(playerInventory.player, input, result, 0, 124, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(input, column + row * 3, 30 + column * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    private static void updateResult(ScreenHandler handler, World world, PlayerEntity player,
                                     RecipeInputInventory input, CraftingResultInventory result) {
        if (world.isClient) {
            return;
        }
        ItemStack stack = ItemStack.EMPTY;
        Optional<EngineeringRecipe> recipe = world.getRecipeManager().getFirstMatch(ModRecipes.ENGINEERING, input, world);
        if (recipe.isPresent()) {
            result.setLastRecipe(recipe.get());
            stack = recipe.get().craft(input, world.getRegistryManager());
        }
        result.setStack(0, stack);
        handler.setPreviousTrackedSlot(0, stack);
        ((ServerPlayerEntity) player).networkHandler.sendPacket(
                new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, stack));
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        context.run((world, pos) -> updateResult(this, world, player, input, result));
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        context.run((world, pos) -> dropInventory(player, input));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, ModBlocks.ENGINEERING_TABLE);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != result && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index == RESULT) {
            context.run((world, pos) -> stack.getItem().onCraft(stack, world, player));
            if (!insertItem(stack, GRID_END, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(stack, original);
        } else if (index >= GRID_END) {
            if (!insertItem(stack, 1, GRID_END, false)) {
                if (index < INVENTORY_END) {
                    if (!insertItem(stack, INVENTORY_END, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!insertItem(stack, GRID_END, INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!insertItem(stack, GRID_END, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTakeItem(player, stack);
        if (index == RESULT) {
            player.dropItem(stack, false);
        }
        return original;
    }

    /**
     * Слот результата. Обычный слот верстака считает остатки по ванильным рецептам
     * и с нашими рецептами вернул бы ингредиенты обратно, поэтому остатки считаем сами.
     */
    private static class ResultSlot extends CraftingResultSlot {
        private final RecipeInputInventory input;
        private final PlayerEntity player;

        ResultSlot(PlayerEntity player, RecipeInputInventory input, Inventory result, int index, int x, int y) {
            super(player, input, result, index, x, y);
            this.input = input;
            this.player = player;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            onCrafted(stack);
            World world = player.getWorld();
            DefaultedList<ItemStack> remainders = world.getRecipeManager()
                    .getFirstMatch(ModRecipes.ENGINEERING, input, world)
                    .map(recipe -> recipe.getRemainder(input))
                    .orElseGet(() -> DefaultedList.ofSize(input.size(), ItemStack.EMPTY));

            for (int i = 0; i < remainders.size(); i++) {
                ItemStack current = input.getStack(i);
                ItemStack remainder = remainders.get(i);
                if (!current.isEmpty()) {
                    input.removeStack(i, 1);
                    current = input.getStack(i);
                }
                if (!remainder.isEmpty()) {
                    if (current.isEmpty()) {
                        input.setStack(i, remainder);
                    } else if (ItemStack.canCombine(current, remainder)) {
                        remainder.increment(current.getCount());
                        input.setStack(i, remainder);
                    } else if (!this.player.getInventory().insertStack(remainder)) {
                        this.player.dropItem(remainder, false);
                    }
                }
            }
        }
    }
}
