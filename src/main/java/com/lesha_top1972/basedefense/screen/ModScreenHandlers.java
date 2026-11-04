package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** Все типы меню мода. */
public final class ModScreenHandlers {

    /** Меню поселенца (снаряжение, сумка, одежда). Клиенту передаётся номер сущности. */
    public static final ScreenHandlerType<GuardScreenHandler> GUARD = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "guard"),
            new ExtendedScreenHandlerType<>(GuardScreenHandler::new));

    /** Меню Повестки (отряды стражников). Клиенту передаётся список стражников и отрядов. */
    public static final ScreenHandlerType<SquadScreenHandler> SQUAD = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "squad"),
            new ExtendedScreenHandlerType<>(SquadScreenHandler::new));

    /** Меню стола амулетов (два амулета + пустой кулон → новый амулет). */
    public static final ScreenHandlerType<AmuletTableScreenHandler> AMULET_TABLE = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "amulet_table"),
            new ScreenHandlerType<>(AmuletTableScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    /** Меню рюкзака (2 или 3 ряда). Клиенту передаются ряды и хозяин рюкзака. */
    public static final ScreenHandlerType<com.lesha_top1972.basedefense.clothing.BackpackScreenHandler> BACKPACK =
            Registry.register(Registries.SCREEN_HANDLER, new Identifier(Basedefense.MOD_ID, "backpack"),
                    new ExtendedScreenHandlerType<>(com.lesha_top1972.basedefense.clothing.BackpackScreenHandler::new));

    private ModScreenHandlers() {
    }

    public static void init() {
        // Пустой метод: вызов нужен, чтобы класс загрузился и меню зарегистрировалось.
    }
}
