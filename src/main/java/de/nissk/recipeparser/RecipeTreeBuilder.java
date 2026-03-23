package de.nissk.recipeparser;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a RecipeNode tree for a target item and computes x/y layout.
 *
 * Mirrors RecipeTree.recipe_to_node() + _layout() from the Python project.
 */
public class RecipeTreeBuilder {

    // ── Node dimensions (in "world" units, scaled on screen) ──────────────
    public static final int NODE_W = 160;
    public static final int NODE_H = 36;
    public static final int H_GAP  = 24;   // horizontal gap between siblings
    public static final int V_GAP  = 56;   // vertical gap between levels

    /** Max recursion depth – prevents loops on circular modpack recipes. */
    private static final int DEFAULT_MAX_DEPTH = 12;

    // visited set prevents infinite loops (same item appearing multiple times)
    private final Set<String> visiting = new HashSet<>();

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Build the full recipe tree for {@code target}.
     * Call {@link #layout(RecipeNode)} afterwards to get x/y positions.
     */
    public RecipeNode build(ItemStack target) {
        return build(target, target.getCount() < 1 ? 1 : target.getCount(), DEFAULT_MAX_DEPTH);
    }

    public RecipeNode build(ItemStack target, int requestedAmount, int maxDepth) {
        visiting.clear();
        return buildNode(target, requestedAmount, maxDepth);
    }

    // ── Tree construction ──────────────────────────────────────────────────

    private RecipeNode buildNode(ItemStack target, int requestedAmount, int depth) {
        String key = target.getHoverName().getString().toLowerCase();

        // Leaf: no recipe, already being expanded, or depth limit reached
        RecipeParser.ParsedRecipe recipe = RecipeParser.findRecipe(target);
        if (recipe == null || depth <= 0 || visiting.contains(key)) {
            return new RecipeNode(target, requestedAmount, "");
        }

        visiting.add(key);

        int outputAmount = recipe.outputs().isEmpty()
                ? 1
                : Math.max(1, recipe.outputs().get(0).getCount());

        // How many crafting operations ("batches") are needed?
        int batches = (int) Math.ceil((double) requestedAmount / outputAmount);

        RecipeNode node = new RecipeNode(target, requestedAmount, recipe.machine());

        // Merge duplicate ingredients (e.g. 4× String → String x4)
        java.util.LinkedHashMap<String, Integer> merged = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, ItemStack> mergedStacks = new java.util.LinkedHashMap<>();
        for (ItemStack input : recipe.inputs()) {
            String key2 = input.getHoverName().getString().toLowerCase();
            int effectiveAmount = Math.max(1, input.getCount() * batches);
            merged.merge(key2, effectiveAmount, Integer::sum);
            mergedStacks.putIfAbsent(key2, input);
        }

        for (java.util.Map.Entry<String, Integer> entry : merged.entrySet()) {
            ItemStack base = mergedStacks.get(entry.getKey());
            ItemStack scaledInput = base.copyWithCount(entry.getValue());

            // Cycle-look-ahead: if this child's recipe only produces items
            // that are ancestors of the current node, treat it as a leaf
            // to avoid expanding e.g. Block of Redstone → Redstone Dust x9
            // when Redstone Dust is already in the visiting set.
            if (wouldCycle(scaledInput)) {
                // Zyklus erkannt → Kind komplett weglassen, Elternknoten wird Leaf
            } else {
                node.children.add(buildNode(scaledInput, entry.getValue(), depth - 1));
            }
        }

        visiting.remove(key);
        return node;
    }

    /**
     * Returns true if every ingredient of the child's recipe is already
     * in the visiting set (i.e. all inputs are ancestors → expanding would cycle).
     * A child with no recipe never cycles.
     */
    private boolean wouldCycle(ItemStack child) {
        RecipeParser.ParsedRecipe recipe = RecipeParser.findRecipe(child);
        if (recipe == null || recipe.inputs().isEmpty()) return false;
        return recipe.inputs().stream().allMatch(input ->
                visiting.contains(input.getHoverName().getString().toLowerCase())
        );
    }

    // ── Layout (Reingold-Tilford inspired) ─────────────────────────────────

    /**
     * Assign x, y to every node in the subtree rooted at {@code root}.
     * After this call, (node.x, node.y) are in "tree world" coordinates.
     * The caller translates to screen coordinates via offsetX/offsetY + scale.
     */
    public static void layout(RecipeNode root) {
        computeLayout(root, 0);
        // Shift so the leftmost node starts at NODE_W/2 + 8 (small left margin)
        float minX = findMinX(root);
        shiftSubtree(root, -minX + NODE_W / 2.0f + 8);
    }

    /** Returns the "width" consumed by this subtree. Also sets node.y. */
    private static float computeLayout(RecipeNode node, int depth) {
        node.y = depth * (NODE_H + V_GAP) + NODE_H / 2.0f + 8;

        if (node.children.isEmpty()) {
            node.x = NODE_W / 2.0f;
            return NODE_W;
        }

        float[] childWidths = new float[node.children.size()];
        float totalWidth = 0;

        for (int i = 0; i < node.children.size(); i++) {
            childWidths[i] = computeLayout(node.children.get(i), depth + 1);
        }
        for (float w : childWidths) totalWidth += w;
        totalWidth += (float) H_GAP * (childWidths.length - 1);

        // Place children left-to-right
        float cursor = 0;
        for (int i = 0; i < node.children.size(); i++) {
            shiftSubtree(node.children.get(i), cursor);
            cursor += childWidths[i] + H_GAP;
        }

        // Center parent over children
        node.x = (node.children.get(0).x + node.children.get(node.children.size() - 1).x) / 2.0f;

        return Math.max(totalWidth, NODE_W);
    }

    private static void shiftSubtree(RecipeNode node, float dx) {
        node.x += dx;
        for (RecipeNode child : node.children) shiftSubtree(child, dx);
    }

    private static float findMinX(RecipeNode node) {
        float min = node.x;
        for (RecipeNode child : node.children) min = Math.min(min, findMinX(child));
        return min;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Returns the total tree width (for fit-to-screen calculation). */
    public static float treeWidth(RecipeNode root) {
        return findMaxX(root) - findMinX(root) + NODE_W;
    }

    /** Returns the total tree height. */
    public static float treeHeight(RecipeNode root) {
        return findMaxY(root) + NODE_H / 2.0f + 8;
    }

    private static float findMaxX(RecipeNode node) {
        float max = node.x;
        for (RecipeNode child : node.children) max = Math.max(max, findMaxX(child));
        return max;
    }

    private static float findMaxY(RecipeNode node) {
        float max = node.y;
        for (RecipeNode child : node.children) max = Math.max(max, findMaxY(child));
        return max;
    }
}
