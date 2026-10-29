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

    public static final ScreenHandlerType<BaseScreenHandler> BASE = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "base"),
            new ScreenHandlerType<>(BaseScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<SettlerScreenHandler> SETTLER = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "settler"),
            new ScreenHandlerType<>(SettlerScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    /** Меню блока дома (стройплощадки). Клиенту передаются позиция и вид дома. */
    public static final ScreenHandlerType<HouseScreenHandler> HOUSE = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "house"),
            new ExtendedScreenHandlerType<>(HouseScreenHandler::new));

    /** Меню строительного планшета (выбор постройки). */
    public static final ScreenHandlerType<TabletScreenHandler> TABLET = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "tablet"),
            new ScreenHandlerType<>(TabletScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    /** Меню военкомата (вербовка стражников). */
    public static final ScreenHandlerType<RecruitScreenHandler> RECRUIT = Registry.register(
            Registries.SCREEN_HANDLER,
            new Identifier(Basedefense.MOD_ID, "recruit"),
            new ScreenHandlerType<>(RecruitScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    /** Меню стражника (снаряжение). Клиенту передаётся номер сущности. */
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

    private ModScreenHandlers() {
    }

    public static void init() {
        // Пустой метод: вызов нужен, чтобы класс загрузился и меню зарегистрировалось.
    }
}
