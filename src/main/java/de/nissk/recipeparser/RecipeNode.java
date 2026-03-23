package de.nissk.recipeparser;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A single node in the recipe tree.
 * Analogous to the Python RecipeNode dataclass.
 */
public class RecipeNode {

    public final ItemStack stack;        // The item this node produces/needs
    public final int       displayAmount; // Scaled amount (accounting for batches)
    public final String    machine;       // e.g. "Crafting Table", "Furnace"

    public final List<RecipeNode> children = new ArrayList<>();

    // Layout coordinates – set by RecipeTreeBuilder.layout()
    public float x;
    public float y;

    public RecipeNode(ItemStack stack, int displayAmount, String machine) {
        this.stack         = stack.copy();
        this.displayAmount = displayAmount;
        this.machine       = machine != null ? machine : "";
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public String getItemName() {
        return stack.getHoverName().getString();
    }
}
