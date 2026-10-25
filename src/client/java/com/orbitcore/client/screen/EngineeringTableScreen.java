package com.orbitcore.client.screen;

import com.orbitcore.client.science.ClientSpaceProgram;
import com.orbitcore.recipe.EngineeringRecipe;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.science.Research;
import com.orbitcore.screen.EngineeringTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class EngineeringTableScreen extends OrbitScreen<EngineeringTableScreenHandler> {
    public EngineeringTableScreen(EngineeringTableScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        boolean ready = handler.getSlot(EngineeringTableScreenHandler.RESULT).hasStack();
        drawProgressArrow(context, left + 90, top + 35, ready ? 1 : 0, 1);

        // Рецепт подходит, но исследование ещё не сделано — подсказываем какое
        if (!ready && client != null && client.world != null) {
            client.world.getRecipeManager().getFirstMatch(ModRecipes.ENGINEERING, handler.input(), client.world)
                    .map(EngineeringRecipe::research)
                    .filter(id -> !ClientSpaceProgram.get().hasResearch(id))
                    .map(Research::byId)
                    .ifPresent(research -> {
                        Text text = Text.translatable("gui.orbitcore.research.needed", Text.translatable(research.translationKey()));
                        context.drawTextWrapped(textRenderer, text, left + 96, top + 60, 74, OrbitStyle.RED);
                    });
        }
    }
}
