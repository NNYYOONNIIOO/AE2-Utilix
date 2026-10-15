package com.ae2utilix.ae2_utilix.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class AE2UtilixConfig {

public static boolean renderFloatingItem = true;

    public static boolean renderFloatingItemModel = true;

    public static float floatingParticleDensity = 0.4F;
    public static boolean enableCpuAccessMode = true;
    public static String decomposerHudPosition = "bottom_left";

    // Registration switches. These are read before mixins are selected, so a
    // disabled feature does not leave its integration mixins active.
    public static boolean registerPhaseInterface = true;
    public static boolean registerCommonInterface = true;
    public static boolean registerCrystalGrowthChamber = true;
    public static boolean registerBlockStorageBus = true;
    public static boolean registerCommonBuses = true;
    public static boolean registerProductReturnCard = true;
    public static boolean registerPhaseCard = true;
    public static boolean registerCouplingStaff = true;
    public static boolean registerMatterDecomposer = true;
    public static boolean registerMEChannelCaller = true;

    /** Logs crystal growth chamber ticks and recipe loading. */
    public static boolean debugCrystalGrowth = false;


    private static File loadedFile;

    public static void load(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            renderFloatingItem = config.getBoolean("renderFloatingItem", "client", true,
                    "Render floating item and particles in Crystal Growth Chamber");

            renderFloatingItemModel = config.getBoolean(
                    "renderFloatingItemModel",
                    "client",
                    true,
                    "Render the floating item model in the Crystal Growth Chamber"
            );

            floatingParticleDensity = config.getFloat(
                    "floatingParticleDensity",
                    "client",
                    0.4F,
                    0.0F,
                    1.0F,
                    "Crystal Growth Chamber particle effect density"
            );
            enableCpuAccessMode = config.getBoolean("enableCpuAccessMode", "common", true,
                    "Enable CPU access mode buttons in Crafting Status GUI (ALL/Player Only/Automation Only)");
            decomposerHudPosition = config.getString("decomposerHudPosition", "client", "bottom_left",
                    "Position of Matter Decomposer HUD overlay [bottom_left, center_left, top_left, center_top, top_right, center_right, bottom_right]",
                    new String[]{"bottom_left", "center_left", "top_left", "center_top", "top_right", "center_right", "bottom_right"});

            registerPhaseInterface = config.getBoolean("registerPhaseInterface", "registration", true,
                    "Register the Phase Interface block and its linked card support.");
            registerCommonInterface = config.getBoolean("registerCommonInterface", "registration", true,
                    "Register the Common ME Interface block and its resource integrations.");
            registerCrystalGrowthChamber = config.getBoolean("registerCrystalGrowthChamber", "registration", true,
                    "Register the Crystal Growth Chamber and its parallel-card support.");

            registerBlockStorageBus = config.getBoolean("registerBlockStorageBus", "registration", true,
                    "Register the Block Storage Bus part.");
            registerCommonBuses = config.getBoolean("registerCommonBuses", "registration", true,
                    "Register the Common Import Bus and Common Export Bus parts.");
            registerProductReturnCard = config.getBoolean("registerProductReturnCard", "registration", true,
                    "Register the Product Return Card.");
            registerPhaseCard = config.getBoolean("registerPhaseCard", "registration", true,
                    "Register the Phase Card.");
            registerCouplingStaff = config.getBoolean("registerCouplingStaff", "registration", true,
                    "Register the Coupling Staff.");
            registerMatterDecomposer = config.getBoolean("registerMatterDecomposer", "registration", true,
                    "Register the Matter Decomposer.");
            registerMEChannelCaller = config.getBoolean("registerMEChannelCaller", "registration", true,
                    "Register the ME Channel Caller and its switchable channel transport support.");
            debugCrystalGrowth = config.getBoolean("debugCrystalGrowth", "debug", false,
                    "Log crystal growth chamber ticking and recipe loading for diagnosis.");

        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
        loadedFile = configFile;
    }

    /**
     * MixinBooter asks for mixin configurations before the mod preInit event.
     * Loading the same Forge configuration here lets registration switches also
     * control whether their corresponding mixins are selected.
     */
    public static void loadEarly(File configFile) {
        if (loadedFile == null || !loadedFile.equals(configFile)) {
            load(configFile);
        }
    }
}
