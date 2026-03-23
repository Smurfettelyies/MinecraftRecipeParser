package de.nissk.recipeparser;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.lwjgl.glfw.GLFW;

public class RecipeTreeButton {

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.recipeparserforrecipetreetool.open_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.recipeparserforrecipetreetool"
    );

    // ── MOD bus ───────────────────────────────────────────────────────────

    @EventBusSubscriber(modid = "recipeparserforrecipetreetool", value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_KEY);
        }
    }

    // ── GAME bus ──────────────────────────────────────────────────────────

    @EventBusSubscriber(modid = "recipeparserforrecipetreetool", value = Dist.CLIENT)
    public static class GameEvents {

        /** J-Taste: öffnet RecipeTreeScreen für das Item in der Hand */
        @SubscribeEvent
        public static void onClientTick(LevelTickEvent.Post event) {
            while (OPEN_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.screen != null) continue;

                ItemStack held = mc.player.getMainHandItem();
                if (held.isEmpty()) held = mc.player.getOffhandItem();

                mc.setScreen(new RecipeTreeScreen(held.isEmpty() ? null : held));
            }
        }

        /** Button links neben dem Inventar hinzufügen */
        @SubscribeEvent
        public static void onInventoryInit(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof InventoryScreen inv)) return;

            int x = inv.getGuiLeft() - 26;
            int y = inv.getGuiTop() + 4;

            event.addListener(new RecipeTreeIconButton(x, y,
                    btn -> Minecraft.getInstance().setScreen(new RecipeTreeScreen(null))
            ));
        }
    }
}
