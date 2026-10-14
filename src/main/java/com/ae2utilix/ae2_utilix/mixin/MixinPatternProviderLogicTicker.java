package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.helpers.patternprovider.PatternProviderLogic;
import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.integration.AE2UtilixPatternProviderAccess;
import com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler;
import com.ae2utilix.ae2_utilix.integration.ProductReturnTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the magnet card and product return card for AE2's own pattern providers.
 *
 * <p>The ticking callbacks live on the private {@code PatternProviderLogic$Ticker} inner
 * class, not on {@code PatternProviderLogic} itself. Injecting into the outer class silently
 * did nothing, which is why the upgrades never ran. The inner class keeps a synthetic
 * {@code this$0} reference back to the logic instance, which is shadowed here to reach the
 * machine's node, host and upgrade inventory.</p>
 */
@Mixin(targets = "ae2.helpers.patternprovider.PatternProviderLogic$Ticker")
public abstract class MixinPatternProviderLogicTicker {

    @Shadow(remap = false)
    @Final
    private PatternProviderLogic this$0;

    @Unique
    private int ae2utilix$magnetTicker;

    @Unique
    private boolean ae2utilix$lastMagnetState;

    @Unique
    private double ae2utilix$baseIdlePower = -1.0;

    @Unique
    private static final double AE2UTILIX_MAGNET_POWER_MULTIPLIER = 10.0;

    /** The provider this ticker belongs to. */
    @Unique
    private PatternProviderLogic ae2utilix$logic() {
        return this.this$0;
    }

    /** The logic instance, viewed through the mod's accessor interface. */
    @Unique
    private com.ae2utilix.ae2_utilix.integration.AE2UtilixPatternProviderAccess ae2utilix$access() {
        return (com.ae2utilix.ae2_utilix.integration.AE2UtilixPatternProviderAccess) (Object) this.this$0;
    }

    /**
     * Keeps the provider awake only while it actually has periodic work: a magnet card
     * always scans for drops, the return card only while outputs are outstanding.
     */
    @Inject(method = "getTickingRequest", at = @At("RETURN"), remap = false, require = 1)
    private void ae2utilix$stayAwakeForUpgrades(IGridNode node,
                                                CallbackInfoReturnable<TickingRequest> cir) {
        TickingRequest original = cir.getReturnValue();
        if (original == null || !original.isSleeping()) {
            return;
        }
        var upgrades = this.ae2utilix$logic().getUpgrades();
        boolean hasMagnet = MachineMagnetHandler.hasMagnetCard(upgrades);
        boolean needsPolling = this.ae2utilix$tracker().isWaiting();
        if (hasMagnet || needsPolling) {
            cir.setReturnValue(new TickingRequest(original.minTickRate(), original.maxTickRate(), false));
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), remap = false, require = 1)
    private void ae2utilix$periodicWork(IGridNode node, int ticksSinceLastCall,
                                        CallbackInfoReturnable<TickRateModulation> cir) {
        if (!this.ae2utilix$access().ae2utilix$mainNode().isActive()) {
            return;
        }
        var upgrades = this.ae2utilix$logic().getUpgrades();
        boolean hasMagnet = MachineMagnetHandler.hasMagnetCard(upgrades);

        if (hasMagnet != this.ae2utilix$lastMagnetState) {
            this.ae2utilix$lastMagnetState = hasMagnet;
            if (node instanceof ae2.me.GridNode gridNode) {
                if (this.ae2utilix$baseIdlePower < 0.0) {
                    this.ae2utilix$baseIdlePower = gridNode.getIdlePowerUsage();
                }
                gridNode.setIdlePowerUsage(hasMagnet
                        ? this.ae2utilix$baseIdlePower * AE2UTILIX_MAGNET_POWER_MULTIPLIER
                        : this.ae2utilix$baseIdlePower);
            }
        }

        if (hasMagnet) {
            if (cir.getReturnValue() == TickRateModulation.SLEEP) {
                cir.setReturnValue(TickRateModulation.SLOWER);
            }
            if (++this.ae2utilix$magnetTicker >= MachineMagnetHandler.SCAN_INTERVAL) {
                this.ae2utilix$magnetTicker = 0;
                MachineMagnetHandler.doMagnetWork(node);
            }
        }

        ae2utilix$pollProductReturn(node, cir);
    }

    /** Polls the push target for products the return card expects. */
    @Unique
    private void ae2utilix$pollProductReturn(IGridNode node,
                                             CallbackInfoReturnable<TickRateModulation> cir) {
        ProductReturnTracker tracker = this.ae2utilix$tracker();
        if (!tracker.isWaiting()) {
            // Nothing outstanding: let the provider sleep again.
            return;
        }
        if (cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }

        TileEntity hostTile = this.ae2utilix$access().ae2utilix$host().getTileEntity();
        if (hostTile == null) {
            return;
        }
        ItemStack card = new ItemStack(AE2Utilix.PRODUCT_RETURN_CARD);
        for (EnumFacing side : this.ae2utilix$access().ae2utilix$host().getTargets()) {
            if (side == null) {
                continue;
            }
            BlockPos targetPos = hostTile.getPos().offset(side);
            if (tracker.tick(node, card, hostTile.getWorld(), targetPos)) {
                this.ae2utilix$logic().saveChanges();
                return;
            }
        }
    }

    @Unique
    private ProductReturnTracker ae2utilix$tracker() {
        return this.ae2utilix$access().ae2utilix$tracker();
    }
}