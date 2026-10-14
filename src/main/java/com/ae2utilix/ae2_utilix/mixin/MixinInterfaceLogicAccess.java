package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.networking.IManagedGridNode;
import ae2.helpers.InterfaceLogic;
import com.ae2utilix.ae2_utilix.integration.AE2UtilixInterfaceAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes {@code InterfaceLogic.mainNode} to the ticker mixin. */
@Mixin(InterfaceLogic.class)
public abstract class MixinInterfaceLogicAccess implements AE2UtilixInterfaceAccess {

    @Shadow(remap = false)
    @Final
    protected IManagedGridNode mainNode;

    @Override
    public IManagedGridNode ae2utilix$mainNode() {
        return this.mainNode;
    }
}