package de.nissk.recipeparser;

import net.minecraft.world.item.ItemStack;

/**
 * Utility helpers – mirrors util.py
 */
public final class RecipeUtil {

    private RecipeUtil() {}

    /** "Iron Ingot" + 4  →  "Iron Ingot x4",  amount == 1  →  "Iron Ingot" */
    public static String formatStack(String name, int amount) {
        return amount > 1 ? name + " x" + amount : name;
    }

    /** Case-insensitive equality (strips whitespace). */
    public static boolean matchExact(String a, String b) {
        return a.strip().equalsIgnoreCase(b.strip());
    }

    /** True if both stacks refer to the same item (ignores count/NBT). */
    public static boolean stacksMatch(ItemStack a, ItemStack b) {
        return ItemStack.isSameItem(a, b);
    }

    /** Clamp a float value. */
    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
