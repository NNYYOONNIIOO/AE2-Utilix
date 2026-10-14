package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
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
 * Gives AE2's own pattern provider the magnet card pickup behaviour.
 *
 * <p>Unlike {@link MixinInterfaceLogicMagnet}, the provider sleeps when it has nothing to
 * push, so the return value is raised out of {@code SLEEP} while a magnet card is installed
 * to keep the five tick scan running.</p>
 */
@Mixin(PatternProviderLogic.class)
public abstract class MixinPatternProviderLogicMagnet {

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Unique
    private int ae2utilix$magnetTicker;

    @Unique
    private boolean ae2utilix$lastMagnetState;

    @Unique
    private double ae2utilix$baseIdlePower = -1.0;

    @Unique
    private static final double AE2UTILIX_MAGNET_POWER_MULTIPLIER = 10.0;

    /**
     * Inserting the card does not wake a sleeping machine by itself, and a sleeping node
     * never asks for its ticking request again, so the upgrade change has to wake it.
     */
    @Inject(method = "onUpgradesChanged", at = @At("TAIL"), remap = false, require = 0)
    private void ae2utilix$wakeOnUpgradeChange(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    /**
     * A sleeping node never receives {@code tickingRequest} at all, so the magnet card has
     * to keep the machine awake from the ticking request itself. Without this the five tick
     * scan would only run while the provider happened to have pattern work.
     */
    @Inject(method = "getTickingRequest", at = @At("RETURN"), remap = false, require = 0)
    private void ae2utilix$stayAwakeForMagnet(IGridNode node,
                                             CallbackInfoReturnable<TickingRequest> cir) {
        TickingRequest original = cir.getReturnValue();
        if (original == null || !original.isSleeping()) {
            return;
        }
        var upgrades = ((PatternProviderLogic) (Object) this).getUpgrades();
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
        var upgrades = ((PatternProviderLogic) (Object) this).getUpgrades();
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

        // The provider sleeps without work, so keep it awake while the magnet is installed.
        if (cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }

        if (++this.ae2utilix$magnetTicker < MachineMagnetHandler.SCAN_INTERVAL) {
            return;
        }
        this.ae2utilix$magnetTicker = 0;

        MachineMagnetHandler.doMagnetWork(node);
    }
}