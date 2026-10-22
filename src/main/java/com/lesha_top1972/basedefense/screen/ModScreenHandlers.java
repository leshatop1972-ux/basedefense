package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** Все типы меню мода. */
public final class ModScreenHandlers {

    public static final ScreenHandlerType<BaseScreenHandler> BASE = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "base"),
            new ScreenHandlerType<>(BaseScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<SettlerScreenHandler> SETTLER = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "settler"),
            new ScreenHandlerType<>(SettlerScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private ModScreenHandlers() {
    }

    public static void init() {
        // Пустой метод: вызов нужен, чтобы класс загрузился и меню зарегистрировалось.
    }
}
