package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.helpers.InterfaceLogic;
import ae2.helpers.InterfaceLogicHost;
import com.ae2utilix.ae2_utilix.integration.MachineMagnetHandler;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives AE2's own ME interface the magnet card pickup behaviour.
 *
 * <p>The interface already ticks, so the scan is driven from its own ticking callback
 * instead of a second timer. A magnet card also multiplies the idle draw, matching the
 * original mod's behaviour.</p>
 */
@Mixin(InterfaceLogic.class)
public abstract class MixinInterfaceLogicMagnet {

    @Shadow
    @Final
    protected IManagedGridNode mainNode;

    @Shadow
    @Final
    protected InterfaceLogicHost host;

    @Unique
    private int ae2utilix$magnetTicker;

    @Unique
    private boolean ae2utilix$lastMagnetState;

    @Unique
    private double ae2utilix$baseIdlePower = -1.0;

    @Unique
    private static final double AE2UTILIX_MAGNET_POWER_MULTIPLIER = 10.0;

    /** Keeps a sleeping interface awake while a magnet card is installed. */
    @Inject(method = "getTickingRequest", at = @At("RETURN"), remap = false, require = 0)
    private void ae2utilix$stayAwakeForMagnet(IGridNode node,
                                             CallbackInfoReturnable<TickingRequest> cir) {
        TickingRequest original = cir.getReturnValue();
        if (original == null || !original.isSleeping()) {
            return;
        }
        var upgrades = ((InterfaceLogic) (Object) this).getUpgrades();
        if (MachineMagnetHandler.hasMagnetCard(upgrades)) {
            cir.setReturnValue(new TickingRequest(original.minTickRate(), original.maxTickRate(), false));
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), remap = false, require = 0)
    private void ae2utilix$magnetTick(IGridNode node, int ticksSinceLastCall,
                                      CallbackInfoReturnable<TickRateModulation> cir) {
        if (!this.mainNode.isActive()) {
            return;
        }
        var upgrades = ((InterfaceLogic) (Object) this).getUpgrades();
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
        if (++this.ae2utilix$magnetTicker < MachineMagnetHandler.SCAN_INTERVAL) {
            return;
        }
        this.ae2utilix$magnetTicker = 0;

        if (MachineMagnetHandler.doMagnetWork(node)
                && cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }
    }
}