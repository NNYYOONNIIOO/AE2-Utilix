package com.ae2utilix.ae2_utilix.mixin;

import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PatternProviderLogicHost;
import com.ae2utilix.ae2_utilix.block.IPhaseLinkHost;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects the pattern provider's push targets to the block linked by the coupling staff.
 *
 * <p>When the owning block is a phase pattern provider with a valid link, every automatic
 * push (crafting machine plans and plain item/fluid/gas insertion) addresses the linked
 * block position and its recorded face instead of the adjacent block. Providers without a
 * valid link keep behaving exactly like the stock one.</p>
 */
@Mixin(PatternProviderLogic.class)
public abstract class MixinPatternProviderLogic {

    @Shadow(remap = false)
    @Final
    private PatternProviderLogicHost host;

    /** Resolves the linked target, or null when this provider is not phase-linked. */
    @Unique
    private BlockPos ae2utilix$linkedPos() {
        TileEntity tile = this.host.getTileEntity();
        if (!(tile instanceof IPhaseLinkHost)) {
            return null;
        }
        IPhaseLinkHost link = (IPhaseLinkHost) tile;
        if (!link.hasLinkData() || !link.isLinkValid()) {
            return null;
        }
        Integer dim = link.getLinkDimension();
        World level = tile.getWorld();
        if (dim == null || level == null || dim != level.provider.getDimension()) {
            return null;
        }
        return link.getLinkPos();
    }

    /** Linked target face, falling back to the original face. */
    @Unique
    private EnumFacing ae2utilix$linkedFace(EnumFacing fallback) {
        TileEntity tile = this.host.getTileEntity();
        if (!(tile instanceof IPhaseLinkHost)) {
            return fallback;
        }
        IPhaseLinkHost link = (IPhaseLinkHost) tile;
        if (!link.hasLinkData() || !link.isLinkValid()) {
            return fallback;
        }
        EnumFacing face = link.getLinkFace();
        return face != null ? face : fallback;
    }

    @Redirect(
            method = "collectPushTargets",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;offset(Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/util/math/BlockPos;"),
            remap = false,
            require = 0)
    private BlockPos ae2utilix$redirectCollectTargets(BlockPos pos, EnumFacing direction) {
        BlockPos linked = this.ae2utilix$linkedPos();
        return linked != null ? linked : pos.offset(direction);
    }

    @Redirect(
            method = "collectPushTargets",
            at = @At(value = "INVOKE",
                    target = "Lae2/api/implementations/blockentities/ICraftingMachine;of(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Lae2/api/implementations/blockentities/ICraftingMachine;"),
            remap = false,
            require = 0)
    private ICraftingMachine ae2utilix$redirectCraftingMachineLookup(World level, BlockPos pos, EnumFacing side) {
        BlockPos linked = this.ae2utilix$linkedPos();
        if (linked == null) {
            return ICraftingMachine.of(level, pos, side);
        }
        return ICraftingMachine.of(level, linked, this.ae2utilix$linkedFace(side));
    }

    /**
     * After a pattern is pushed, the product return card starts polling the target block so
     * the crafting outputs come back into the network. Only the pure item case is handled
     * here; fluid and gas integration are not part of this port.
     */
    @Inject(method = "onPushPatternSuccess", at = @At("TAIL"), remap = false, require = 0)
    private void ae2utilix$rememberExpectedOutputs(ae2.api.crafting.IPatternDetails pattern,
                                                   CallbackInfo ci) {
        TileEntity tile = this.host.getTileEntity();
        if (!(tile instanceof com.ae2utilix.ae2_utilix.block.TilePhasedPatternProvider phased)) {
            return;
        }
        phased.rememberExpectedOutputs(pattern);
    }

    @Redirect(
            method = "findAdapter",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;offset(Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/util/math/BlockPos;"),
            remap = false,
            require = 0)
    private BlockPos ae2utilix$redirectAdapterTarget(BlockPos pos, EnumFacing side) {
        BlockPos linked = this.ae2utilix$linkedPos();
        return linked != null ? linked : pos.offset(side);
    }
}