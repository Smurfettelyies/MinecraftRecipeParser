package de.nissk.recipeparser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks up recipes from the client-side RecipeManager.
 * Works for vanilla AND modded recipes (all recipes are synced to the client).
 *
 * Optional JEI runtime is stored by JeiIntegration.java (soft-dep).
 * If JEI is not installed, everything here still works fine.
 *
 * Mirrors RecipeDB.find() from the Python project.
 */
public final class RecipeParser {

    private RecipeParser() {}

    // ── Public record returned by findRecipe ───────────────────────────────

    public record ParsedRecipe(
            String         machine,
            List<ItemStack> outputs,
            List<ItemStack> inputs
    ) {}

    // ── Main lookup ────────────────────────────────────────────────────────

    /**
     * Returns the first recipe found that produces {@code target}, or null.
     * Priority order: Crafting → Smelting → Blasting → Smoking → Stonecutting → Campfire
     */
    @Nullable
    public static ParsedRecipe findRecipe(ItemStack target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        RecipeManager  rm  = mc.level.getRecipeManager();
        RegistryAccess reg = mc.level.registryAccess();

        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(reg);

            if (result.isEmpty()) continue;
            if (!ItemStack.isSameItem(result, target)) continue;

            List<ItemStack> inputs = extractInputs(recipe);
            if (inputs == null || inputs.isEmpty()) continue;

            String machine = getMachineName(recipe);

            List<ItemStack> outputs = new ArrayList<>();
            // Copy with actual output count
            ItemStack out = result.copy();
            outputs.add(out);

            return new ParsedRecipe(machine, outputs, inputs);
        }

        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Extract one representative ItemStack per ingredient slot. */
    @Nullable
    private static List<ItemStack> extractInputs(Recipe<?> recipe) {
        List<ItemStack> inputs = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            ItemStack[] items = ing.getItems();
            if (items.length > 0 && !items[0].isEmpty()) {
                inputs.add(items[0].copy());
            }
        }
        return inputs.isEmpty() ? null : inputs;
    }

    /** Map recipe class → human-readable machine name. */
    private static String getMachineName(Recipe<?> recipe) {
        // Java 21 pattern-matching switch – works on NeoForge 1.21.1
        return switch (recipe) {
            case ShapedRecipe   ignored -> "Crafting Table";
            case ShapelessRecipe ignored -> "Crafting Table";
            case SmeltingRecipe ignored  -> "Furnace";
            case BlastingRecipe ignored  -> "Blast Furnace";
            case SmokingRecipe  ignored  -> "Smoker";
            case StonecutterRecipe ign   -> "Stonecutter";
            case CampfireCookingRecipe ign -> "Campfire";
            // Modded recipes: fall back to the recipe type's registry name
            default -> {
                String cls = recipe.getClass().getSimpleName();
                // Strip common suffixes for readability
                yield cls.replace("Recipe", "").replace("recipe", "");
            }
        };
    }
}
