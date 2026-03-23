package de.nissk.recipeparser;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game screen mit Suchfeld + Recipe Tree.
 * - Suchfeld oben: nach Itemname suchen, Enter oder Klick auf Vorschlag
 * - Linke Maustaste halten + ziehen: pan
 * - Scroll: zoom
 * - F: Fit View
 * - ESC: schließen
 */
public class RecipeTreeScreen extends Screen {

    // ── Farben ─────────────────────────────────────────────────────────────
    private static final int BG_COLOR     = 0xFF141414;
    private static final int TOOLBAR_BG   = 0xFF1a1a1a;
    private static final int NODE_COLOR   = 0xFF1e1e1e;
    private static final int LEAF_COLOR   = 0xFF141a14;
    private static final int BORDER_COLOR = 0xFF7ec850;
    private static final int LEAF_BORDER  = 0xFF2e4e2e;
    private static final int EDGE_COLOR   = 0xFF3a3a3a;
    private static final int TEXT_COLOR   = 0xFFd0d0c8;
    private static final int DIM_COLOR    = 0xFF606060;
    private static final int ACCENT       = 0xFF7ec850;
    private static final int HINT_COLOR   = 0xFF888880;

    private static final int TOOLBAR_H  = 26;
    private static final int SUGGEST_H  = 12;

    // ── State ──────────────────────────────────────────────────────────────
    @Nullable private RecipeNode root;
    private float scale   = 1.0f;
    private float offsetX = 0;
    private float offsetY = 0;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;

    // Suche
    private EditBox searchBox;
    private final List<String> suggestions = new ArrayList<>();
    private int selectedSuggestion = -1;

    // ── Konstruktor ────────────────────────────────────────────────────────

    public RecipeTreeScreen(@Nullable ItemStack initial) {
        super(Component.literal("Recipe Tree"));
        if (initial != null && !initial.isEmpty()) {
            buildTree(initial);
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(font, width / 2 - 100, 4, 200, 18,
                Component.literal("Search item..."));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search item..."));
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setBordered(true);
        addRenderableWidget(searchBox);

        if (root != null) fitView();
    }

    // ── Baum bauen ─────────────────────────────────────────────────────────

    private void buildTree(ItemStack stack) {
        RecipeTreeBuilder builder = new RecipeTreeBuilder();
        root = builder.build(stack);
        RecipeTreeBuilder.layout(root);
    }

    private void fitView() {
        if (root == null) return;
        float treeW = RecipeTreeBuilder.treeWidth(root);
        float treeH = RecipeTreeBuilder.treeHeight(root);
        float pad   = 56;
        float availH = height - TOOLBAR_H - pad;

        float scaleX = (width  - 2 * pad) / Math.max(treeW, 1);
        float scaleY = availH            / Math.max(treeH, 1);
        scale   = RecipeUtil.clamp(Math.min(scaleX, scaleY), 0.15f, 2.0f);
        offsetX = pad + ((width  - 2 * pad) - treeW * scale) / 2.0f;
        offsetY = TOOLBAR_H + pad / 2;
    }

    // ── Suche ──────────────────────────────────────────────────────────────

    private void onSearchChanged(String text) {
        suggestions.clear();
        selectedSuggestion = -1;
        if (text.length() < 2) return;

        String lower = text.strip().toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getDefaultInstance().getHoverName().getString();
            if (name.toLowerCase().contains(lower)) {
                suggestions.add(name);
                if (suggestions.size() >= 8) break;
            }
        }
    }

    private void confirmSearch(String name) {
        // Item anhand des Anzeigenamens finden
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.getHoverName().getString().equalsIgnoreCase(name)) {
                buildTree(stack);
                fitView();
                suggestions.clear();
                searchBox.setValue(name);
                return;
            }
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Solider Hintergrund – überschreibt das vanilla Blur/Dirt-Rendering
        g.fill(0, 0, width, height, BG_COLOR);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1) Hintergrund
        renderBackground(g, mouseX, mouseY, partialTick);

        // 2) Toolbar
        g.fill(0, 0, width, TOOLBAR_H, TOOLBAR_BG);
        g.drawString(font, "RECIPE TREE", 6, 9, ACCENT, false);
        g.drawString(font, "F: fit  |  ESC: close", width - 120, 9, DIM_COLOR, false);

        // 3) Baum
        if (root != null) {
            // Kanten + Node-Hintergründe im transformierten Raum
            var ps = g.pose();
            ps.pushPose();
            ps.translate(offsetX, offsetY, 0);
            ps.scale(scale, scale, 1.0f);
            drawEdges(g, root);
            drawNodeBGs(g, root);
            ps.popPose();
            // Items + Text in Screen-Koordinaten
            drawNodeLabels(g, root);
        } else {
            String hint = "Search for an item above";
            g.drawCenteredString(font, hint, width / 2, height / 2, DIM_COLOR);
        }

        // 4) Widgets (EditBox) – NACH dem Baum, damit sie oben liegen
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        // 5) Autocomplete Dropdown
        renderSuggestions(g, mouseX, mouseY);
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (suggestions.isEmpty()) return;

        int sx = searchBox.getX();
        int sy = searchBox.getY() + searchBox.getHeight() + 1;
        int sw = searchBox.getWidth();

        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + suggestions.size() * SUGGEST_H + 3, BORDER_COLOR);
        g.fill(sx, sy, sx + sw, sy + suggestions.size() * SUGGEST_H + 2, 0xFF0f0f0f);

        for (int i = 0; i < suggestions.size(); i++) {
            int iy = sy + 2 + i * SUGGEST_H;
            boolean hover = mouseX >= sx && mouseX <= sx + sw
                    && mouseY >= iy && mouseY <= iy + SUGGEST_H;
            boolean selected = i == selectedSuggestion;
            if (hover || selected) {
                g.fill(sx, iy - 1, sx + sw, iy + SUGGEST_H - 1, 0xFF2a3a2a);
            }
            g.drawString(font, suggestions.get(i), sx + 3, iy, TEXT_COLOR, false);
        }
    }

    // ── Node rendering ─────────────────────────────────────────────────────

    /** Nur Hintergründe + Borders – läuft innerhalb des skalierten PoseStack. */
    private void drawNodeBGs(GuiGraphics g, RecipeNode node) {
        int nw = RecipeTreeBuilder.NODE_W;
        int nh = RecipeTreeBuilder.NODE_H;
        int x0 = Math.round(node.x - nw / 2.0f);
        int y0 = Math.round(node.y - nh / 2.0f);
        int x1 = x0 + nw;
        int y1 = y0 + nh;
        int fill   = node.isLeaf() ? LEAF_COLOR  : NODE_COLOR;
        int border = node.isLeaf() ? LEAF_BORDER : BORDER_COLOR;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
        g.fill(x0, y0, x1, y1, fill);
        for (RecipeNode child : node.children) drawNodeBGs(g, child);
    }

    /** Hilfsmethode: Tree-Koordinate → Screen-Pixel */
    private int tx(float treeX) { return Math.round(treeX * scale + offsetX); }
    private int ty(float treeY) { return Math.round(treeY * scale + offsetY); }

    /** Items + Text – läuft in normalen Screen-Koordinaten. */
    private void drawNodeLabels(GuiGraphics g, RecipeNode node) {
        int nw = RecipeTreeBuilder.NODE_W;
        int nh = RecipeTreeBuilder.NODE_H;

        int sx0 = tx(node.x - nw / 2.0f);
        int sy0 = ty(node.y - nh / 2.0f);
        int sy1 = ty(node.y + nh / 2.0f);
        int snw = Math.max(1, Math.round(nw * scale));

        // Item-Icon (16×16 px, immer gleich groß)
        g.renderItem(node.stack, sx0 + 4, ty(node.y) - 8);

        // Label – Schrift skaliert mit
        String label = RecipeUtil.formatStack(node.getItemName(), node.displayAmount);
        int maxW = snw - 28;
        if (maxW > 0 && font.width(label) > maxW) {
            label = font.plainSubstrByWidth(label, maxW - font.width("..")) + "..";
        }
        if (maxW > 0) {
            g.drawString(font, label, sx0 + 22, ty(node.y) - 4, TEXT_COLOR, false);
        }

        // Machine-Label unter dem Node
        if (!node.machine.isEmpty() && !node.isLeaf() && scale > 0.5f) {
            String ml = "[ " + node.machine + " ]";
            g.drawString(font, ml, tx(node.x) - font.width(ml) / 2, sy1 + 2, DIM_COLOR, false);
        }

        for (RecipeNode child : node.children) drawNodeLabels(g, child);
    }

    // ── Edge rendering ─────────────────────────────────────────────────────

    private void drawEdges(GuiGraphics g, RecipeNode node) {
        float botY = node.y + RecipeTreeBuilder.NODE_H / 2.0f;
        for (RecipeNode child : node.children) {
            float topY = child.y - RecipeTreeBuilder.NODE_H / 2.0f;
            float midY = (botY + topY) / 2.0f;
            hLine(g, node.x, child.x, midY);
            vLine(g, node.x, botY, midY);
            vLine(g, child.x, midY, topY);
            drawEdges(g, child);
        }
    }

    private void hLine(GuiGraphics g, float x1, float x2, float y) {
        g.fill(Math.round(Math.min(x1, x2)), Math.round(y),
               Math.round(Math.max(x1, x2)) + 1, Math.round(y) + 1, EDGE_COLOR);
    }

    private void vLine(GuiGraphics g, float x, float y1, float y2) {
        g.fill(Math.round(x), Math.round(Math.min(y1, y2)),
               Math.round(x) + 1, Math.round(Math.max(y1, y2)) + 1, EDGE_COLOR);
    }

    // ── Input ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // Scroll im Dropdown → Suggestion navigieren
        if (!suggestions.isEmpty() && mx >= searchBox.getX()
                && mx <= searchBox.getX() + searchBox.getWidth()
                && my >= searchBox.getY() + searchBox.getHeight()) {
            selectedSuggestion = (int) RecipeUtil.clamp(
                    selectedSuggestion + (scrollY > 0 ? -1 : 1),
                    0, suggestions.size() - 1);
            return true;
        }

        float factor   = scrollY > 0 ? 1.12f : (1.0f / 1.12f);
        float newScale = RecipeUtil.clamp(scale * factor, 0.10f, 4.0f);
        offsetX = (float) (mx + (offsetX - mx) * newScale / scale);
        offsetY = (float) (my + (offsetY - my) * newScale / scale);
        scale   = newScale;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Klick auf Suggestion
        if (!suggestions.isEmpty() && button == 0) {
            int sx = searchBox.getX();
            int sy = searchBox.getY() + searchBox.getHeight() + 1;
            int sw = searchBox.getWidth();
            for (int i = 0; i < suggestions.size(); i++) {
                int iy = sy + 2 + i * SUGGEST_H;
                if (mx >= sx && mx <= sx + sw && my >= iy && my <= iy + SUGGEST_H) {
                    confirmSearch(suggestions.get(i));
                    return true;
                }
            }
        }

        if (button == 0 && my > TOOLBAR_H) {
            dragging   = true;
            lastMouseX = mx;
            lastMouseY = my;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            offsetX += (float) (mx - lastMouseX);
            offsetY += (float) (my - lastMouseY);
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter im Suchfeld
        if (keyCode == 257 /* GLFW_KEY_ENTER */ && searchBox.isFocused()) {
            if (selectedSuggestion >= 0 && selectedSuggestion < suggestions.size()) {
                confirmSearch(suggestions.get(selectedSuggestion));
            } else if (!searchBox.getValue().isBlank()) {
                confirmSearch(searchBox.getValue().strip());
            }
            return true;
        }
        // Pfeil rauf/runter in Suggestions
        if (keyCode == 264 /* DOWN */ && !suggestions.isEmpty()) {
            selectedSuggestion = (int) RecipeUtil.clamp(selectedSuggestion + 1, 0, suggestions.size() - 1);
            return true;
        }
        if (keyCode == 265 /* UP */ && !suggestions.isEmpty()) {
            selectedSuggestion = (int) RecipeUtil.clamp(selectedSuggestion - 1, 0, suggestions.size() - 1);
            return true;
        }
        // ESC: erst Suchfeld defokussieren, dann Screen schließen
        if (keyCode == 256 /* GLFW_KEY_ESCAPE */) {
            if (searchBox.isFocused()) {
                searchBox.setFocused(false);
                suggestions.clear();
                selectedSuggestion = -1;
                return true;
            }
            this.onClose();
            return true;
        }
        // F → Fit View
        if (keyCode == 70) {
            fitView();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // Verhindert dass die Spielwelt durchscheint
    }
}
