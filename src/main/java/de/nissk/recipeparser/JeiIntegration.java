package de.nissk.recipeparser;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

@JeiPlugin
public class JeiIntegration implements IModPlugin {

    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("recipeparserforrecipetreetool", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static boolean isAvailable() {
        return runtime != null;
    }

    @Nullable
    public static IJeiRuntime getRuntime() {
        return runtime;
    }
}