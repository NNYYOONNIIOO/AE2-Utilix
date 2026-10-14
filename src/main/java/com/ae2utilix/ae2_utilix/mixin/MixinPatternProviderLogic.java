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

    /**
     * Face the linked block should be addressed on.
     *
     * <p>A marked phase card wins over the coupling staff link, matching the original mod:
     * the card is the explicit "this side of the machine" instruction, while a link only
     * says which block to talk to.</p>
     */
    @Unique
    private EnumFacing ae2utilix$linkedFace(EnumFacing fallback) {
        // A phase card applies to every pattern provider, AE2's own included: it selects
        // which side of the adjacent machine receives the crafted inputs, and needs no
        // coupling link at all. This must be read before the link host check below, because
        // AE2's pattern provider is not a phase link host and would otherwise bail out here.
        EnumFacing cardFace = com.ae2utilix.ae2_utilix.block.TilePhasedPatternProvider
                .phaseCardFace(this.host.getUpgrades());
        if (cardFace != null) {
            return cardFace;
        }

        // The coupling staff is only available on the phase pattern provider; when one is
        // linked, its recorded face is the fallback for the machine side to address.
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

    /**
     * The direction the provider believes it is pushing towards.
     *
     * <p>Without this, AE2 keeps using the block it physically touches (and the face that
     * points back at the provider) even when a phase card marks a different side. Routing
     * through the marked face is what lets a marked card place inputs into a specific slot
     * group, such as a furnace top rather than its fuel side.</p>
     */
    @Redirect(
            method = "collectPushTargets",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/EnumFacing;getOpposite()Lnet/minecraft/util/EnumFacing;"),
            remap = false,
            require = 0)
    private EnumFacing ae2utilix$redirectAdjacentSide(EnumFacing direction) {
        // The marked face is the machine side to address, so it applies whether or not the
        // block is coupled remotely. Only the position depends on the coupling link.
        return this.ae2utilix$linkedFace(direction.getOpposite());
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

    /**
     * The adapter is built for {@code side.getOpposite()} of the target block. Redirecting
     * that here is what makes the phase card take effect for plain inventories; without it
     * only the crafting machine path below would honour the marked face.
     */
    @Redirect(
            method = "findAdapter",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/EnumFacing;getOpposite()Lnet/minecraft/util/EnumFacing;"),
            remap = false,
            require = 0)
    private EnumFacing ae2utilix$redirectAdapterFace(EnumFacing side) {
        return this.ae2utilix$linkedFace(side.getOpposite());
    }
}