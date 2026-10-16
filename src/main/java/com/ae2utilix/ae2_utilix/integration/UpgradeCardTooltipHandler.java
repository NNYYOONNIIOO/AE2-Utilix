package com.ae2utilix.ae2_utilix.integration;

import ae2.api.upgrades.Upgrades;
import ae2.core.localization.GuiText;
import ae2.text.TextComponentItemStack;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.config.AE2UtilixConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

/**
 * Keeps the crystal growth chamber visible in the speed/parallel card tooltip.
 *
 * <p>Supergiant prints at most three machines per card and sorts them by how many cards the
 * machine accepts, ascending. The chamber accepts five cards, so it always sorts behind the
 * built-in machines and ends up behind the "hold shift" hint. This handler appends the
 * missing line for the cards this mod registered, so the entry is visible without holding
 * shift. Holding shift still leaves the tooltip to Supergiant, which lists every machine.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Utilix.MODID, value = Side.CLIENT)
public final class UpgradeCardTooltipHandler {

    /** Mirrors the limit used by Supergiant's {@code UpgradeCardItem}. */
    private static final int VISIBLE_SUPPORTED_TARGETS = 3;

    private UpgradeCardTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!AE2UtilixConfig.registerCrystalGrowthChamber) {
            return;
        }
        // Supergiant prints the complete list while shift is held, chamber included.
        if (GuiScreen.isShiftKeyDown()) {
            return;
        }

        Item card = event.getItemStack().getItem();
        Item chamber = Item.getItemFromBlock(AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER);
        if (card == null || chamber == null) {
            return;
        }

        int maxInstalled = Upgrades.getMaxInstallable(card, chamber);
        if (maxInstalled <= 0) {
            return;
        }

        List<ITextComponent> supportedBy = Upgrades.getTooltipLinesForCard(card);
        if (supportedBy.size() <= VISIBLE_SUPPORTED_TARGETS) {
            // The whole list fits, so nothing was hidden from the player.
            return;
        }

        String chamberLine = supportedMachineLine(chamber, maxInstalled);
        List<String> tooltip = event.getToolTip();
        if (tooltip.contains(chamberLine)) {
            return;
        }

        // Stay next to the other entries: insert ahead of the shift hint when it is present.
        int insertAt = tooltip.size();
        String shiftHint = GuiText.PressShiftForFullList.getLocal();
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            if (tooltip.get(i).contains(shiftHint)) {
                insertAt = i;
                break;
            }
        }
        tooltip.add(insertAt, chamberLine);
    }

    /** Formats a machine exactly like {@code Upgrades.createTooltipLinesForCard} does. */
    private static String supportedMachineLine(Item machine, int maxInstalled) {
        ITextComponent line = TextComponentItemStack.of(new ItemStack(machine));
        if (maxInstalled > 1) {
            line = line.createCopy().appendSibling(new TextComponentString(" (" + maxInstalled + ")"));
        }
        return line.getFormattedText();
    }
}