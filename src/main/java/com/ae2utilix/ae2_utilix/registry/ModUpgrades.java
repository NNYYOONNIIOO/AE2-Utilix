package com.ae2utilix.ae2_utilix.registry;

import ae2.api.upgrades.Upgrades;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.AEParts;
import ae2.core.localization.GuiText;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import com.ae2utilix.ae2_utilix.upgrade.ModUpgradeRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Registers this mod's upgrade cards against AE2 machines.
 *
 * <p>Supergiant decides which cards a machine accepts through {@link Upgrades#add}, so a card
 * that is never listed here is rejected by every upgrade slot. The same registration drives the
 * "supported by" tooltip list, which is why the display registry is filled in parallel.</p>
 */
public final class ModUpgrades {

    private ModUpgrades() {
    }

    /** Registers all card-to-machine associations. Call once during pre-init. */
    public static void register() {
        if (AE2UtilixConfig.registerProductReturnCard && AE2UtilixConfig.registerPhaseInterface) {
            addMachine(AE2Utilix.PRODUCT_RETURN_CARD,
                    net.minecraft.item.Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_PATTERN_PROVIDER), 1);
        }
        if (AE2UtilixConfig.registerPhaseCard && AE2UtilixConfig.registerPhaseInterface) {
            addMachine(AE2Utilix.PHASE_CARD,
                    net.minecraft.item.Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_PATTERN_PROVIDER), 1);
        }

        // These two cards only make sense on the pattern provider: they steer pushed
        // inputs and collect the crafting results, which an ME interface never does.
        if (AE2UtilixConfig.registerProductReturnCard && AEBlocks.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AE2Utilix.PRODUCT_RETURN_CARD, AEBlocks.PATTERN_PROVIDER.item(), 1,
                    GuiText.CraftingInterface.getTranslationKey());
        }
        if (AE2UtilixConfig.registerProductReturnCard && AEParts.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AE2Utilix.PRODUCT_RETURN_CARD, AEParts.PATTERN_PROVIDER.item(), 1,
                    GuiText.CraftingInterface.getTranslationKey());
        }
        if (AE2UtilixConfig.registerPhaseCard && AEBlocks.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AE2Utilix.PHASE_CARD, AEBlocks.PATTERN_PROVIDER.item(), 1,
                    GuiText.CraftingInterface.getTranslationKey());
        }
        if (AE2UtilixConfig.registerPhaseCard && AEParts.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AE2Utilix.PHASE_CARD, AEParts.PATTERN_PROVIDER.item(), 1,
                    GuiText.CraftingInterface.getTranslationKey());
        }

        // Magnet cards are accepted by ME interfaces and pattern providers, matching what the
        // Forge build registered for the interface alone.
        if (AE2UtilixConfig.registerPhaseInterface) {
            Upgrades.add(AEItems.MAGNET_CARD.item(),
                    net.minecraft.item.Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_PATTERN_PROVIDER), 1);
            ModUpgradeRegistry.registerItem(AE2Utilix.UPGRADE_PHASE_CARD,
                    new ItemStack(net.minecraft.item.Item.getItemFromBlock(AE2Utilix.BLOCK_PHASE_PATTERN_PROVIDER)), 1);
        }
        registerMagnetCard();
    }

    private static void addMachine(Item card, Item machine, int maxInstalled) {
        Upgrades.add(card, machine, maxInstalled);
        ModUpgradeRegistry.registerItem(
                card instanceof com.ae2utilix.ae2_utilix.upgrade.IAE2UtilixUpgradeModule module
                        ? module.getUpgradeTypeId()
                        : AE2Utilix.UPGRADE_PHASE_CARD,
                new ItemStack(machine), maxInstalled);
    }

    private static void addAe2InterfaceAndProvider(Item card) {
        String interfaceGroup = GuiText.Interface.getTranslationKey();
        String providerGroup = GuiText.CraftingInterface.getTranslationKey();

        if (AEBlocks.INTERFACE.item() != null) {
            Upgrades.add(card, AEBlocks.INTERFACE.item(), 1, interfaceGroup);
        }
        if (AEParts.INTERFACE.item() != null) {
            Upgrades.add(card, AEParts.INTERFACE.item(), 1, interfaceGroup);
        }
        if (AEBlocks.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(card, AEBlocks.PATTERN_PROVIDER.item(), 1, providerGroup);
        }
        if (AEParts.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(card, AEParts.PATTERN_PROVIDER.item(), 1, providerGroup);
        }
    }

    private static void registerMagnetCard() {
        if (AEBlocks.INTERFACE.item() != null) {
            Upgrades.add(AEItems.MAGNET_CARD.item(), AEBlocks.INTERFACE.item(), 1);
        }
        if (AEParts.INTERFACE.item() != null) {
            Upgrades.add(AEItems.MAGNET_CARD.item(), AEParts.INTERFACE.item(), 1);
        }
        if (AEBlocks.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AEItems.MAGNET_CARD.item(), AEBlocks.PATTERN_PROVIDER.item(), 1);
        }
        if (AEParts.PATTERN_PROVIDER.item() != null) {
            Upgrades.add(AEItems.MAGNET_CARD.item(), AEParts.PATTERN_PROVIDER.item(), 1);
        }
    }
}