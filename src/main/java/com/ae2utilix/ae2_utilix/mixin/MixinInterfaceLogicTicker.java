package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.helpers.InterfaceLogic;
import ae2.helpers.InterfaceLogicHost;
import com.ae2utilix.ae2_utilix.integration.AE2UtilixInterfaceAccess;
import com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Runs the magnet card pickup for AE2's own ME interface.
 *
 * <p>The ticking callbacks live on the private {@code InterfaceLogic$Ticker} inner class, so
 * injecting into {@code InterfaceLogic} itself silently did nothing.</p>
 */
@Mixin(targets = "ae2.helpers.InterfaceLogic$Ticker")
public abstract class MixinInterfaceLogicTicker {

    @Shadow(remap = false)
    @Final
    private InterfaceLogic this$0;

    @Unique
    private int ae2utilix$magnetTicker;

    @Unique
    private boolean ae2utilix$lastMagnetState;

    @Unique
    private double ae2utilix$baseIdlePower = -1.0;

    @Unique
    private static final double AE2UTILIX_MAGNET_POWER_MULTIPLIER = 10.0;

    @Unique
    private AE2UtilixInterfaceAccess ae2utilix$access() {
        return (AE2UtilixInterfaceAccess) (Object) this.this$0;
    }

    @Unique
    private IManagedGridNode ae2utilix$mainNode() {
        return this.ae2utilix$access().ae2utilix$mainNode();
    }

    /** Keeps a sleeping interface awake while a magnet card is installed. */
    @ModifyReturnValue(method = "getTickingRequest", at = @At("RETURN"), remap = false, require = 1)
    private TickingRequest ae2utilix$stayAwakeForMagnet(TickingRequest original, IGridNode node) {
        if (original == null || !original.isSleeping()) {
            return original;
        }
        var upgrades = this.this$0.getUpgrades();
        if (MachineMagnetHandler.hasMagnetCard(upgrades)) {
            return new TickingRequest(original.minTickRate(), original.maxTickRate(), false);
        }
        return original;
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), remap = false, require = 1, cancellable = true)
    private void ae2utilix$magnetTick(IGridNode node, int ticksSinceLastCall,
                                      CallbackInfoReturnable<TickRateModulation> cir) {
        if (!this.ae2utilix$mainNode().isActive()) {
            return;
        }
        var upgrades = this.this$0.getUpgrades();
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

        if (!hasMagnet) {
            return;
        }
        if (cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }
        if (++this.ae2utilix$magnetTicker >= MachineMagnetHandler.SCAN_INTERVAL) {
            this.ae2utilix$magnetTicker = 0;
            MachineMagnetHandler.doMagnetWork(node);
        }
    }
}