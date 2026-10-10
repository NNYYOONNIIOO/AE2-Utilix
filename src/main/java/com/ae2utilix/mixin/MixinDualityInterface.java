package com.ae2utilix.mixin;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.ae2utilix.IProductReturnHost;
import com.ae2utilix.block.TileCommonInterfaceAlternate;
import com.ae2utilix.block.IPhaseLinkHost;
import com.ae2utilix.block.TilePhaseInterface;
import com.ae2utilix.item.ItemPhaseCard;
import com.ae2utilix.item.ItemFluidMark;
import com.ae2utilix.item.ItemProductReturnCard;
import com.ae2utilix.integration.BMCCompat;
import com.ae2utilix.integration.ExtractFaceHelper;
import com.ae2utilix.integration.FluidReturnHandler;
import com.ae2utilix.integration.GasReturnHandler;
import com.ae2utilix.integration.MekanismGasHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

@Mixin(DualityInterface.class)
public abstract class MixinDualityInterface implements IProductReturnHost {

    @Shadow(remap = false)
    private AENetworkProxy gridProxy;

    @Shadow(remap = false)
    private IInterfaceHost iHost;

    @Inject(method = "getTermName", at = @At("HEAD"), cancellable = true, remap = false, require = 0)/*require=0: old ae2fc may transform target*/
    private void ae2utilix$phaseInterfaceTermName(CallbackInfoReturnable<String> cir) {
        if (this.iHost instanceof IPhaseLinkHost) {
            String key = ((IPhaseLinkHost) this.iHost).ae2utilix$getTermNameKey();
            if (key != null) {
                cir.setReturnValue(key);
            }
        }
    }

    @Shadow(remap = false)
    private IActionSource interfaceRequestSource;

    @Shadow(remap = false)
    private IAEItemStack[] requireWork;

    @Shadow(remap = false)
    private boolean hasConfig;

    @Shadow(remap = false)
    public abstract IItemHandler getConfig();

    @Inject(method = "updatePlan", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ae2utilix$skipVirtualFluidItemRequest(final int slot, final CallbackInfo ci) {
        final IItemHandler config = this.getConfig();
        if (config != null && slot >= 0 && slot < config.getSlots()
                && ItemFluidMark.isVirtualMark(config.getStackInSlot(slot))) {
            this.requireWork[slot] = null;
            ci.cancel();
        }
    }

    @Inject(method = "readConfig", at = @At("TAIL"), remap = false, require = 0)
    private void ae2utilix$ignoreVirtualFluidAsItemConfig(final CallbackInfo ci) {
        if (!(this.iHost instanceof TileCommonInterfaceAlternate)) return;

        boolean hasItemConfig = false;
        final IItemHandler config = this.getConfig();
        for (int slot = 0; slot < config.getSlots(); slot++) {
            final ItemStack stack = config.getStackInSlot(slot);
            if (ItemFluidMark.isVirtualMark(stack)) {
                this.requireWork[slot] = null;
            } else if (!stack.isEmpty()) {
                hasItemConfig = true;
            }
        }
        this.hasConfig = hasItemConfig;
    }

    @Unique
    private boolean ae2utilix$isP2PTunnel(TileEntity te, EnumFacing face) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("nae2")) return false;
        try {
            if (te instanceof appeng.api.parts.IPartHost) {
                appeng.api.parts.IPartHost host = (appeng.api.parts.IPartHost) te;
                appeng.api.parts.IPart part = host.getPart(face);
                return part != null && part.getClass().getName().contains("PartP2PInterface");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Unique
    private IPhaseLinkHost ae2utilix$getPhaseInterface() {
        if (!(this.iHost instanceof IPhaseLinkHost)) return null;
        IPhaseLinkHost pi = (IPhaseLinkHost) this.iHost;
        return pi.hasLinkData() && pi.isLinkValid() ? pi : null;
    }

    @Unique
    private EnumFacing ae2utilix$getPhaseInterfaceEffectiveFace() {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi == null) return null;
        EnumFacing phaseCardFace = ae2utilix$getPhaseCardFace();
        if (phaseCardFace != null) return phaseCardFace;
        return pi.getLinkFace();
    }

    @Unique
    private long ae2utilix$extractFluid(TileEntity te, EnumFacing extractFace, IAEItemStack expected, IMEInventory<IAEItemStack> dest, IEnergySource energy) {
        FluidStack expectedFluid = FluidReturnHandler.getFluidFromAEStack(expected);
        if (expectedFluid == null) return 0;

        int toDrain = (int) Math.min(expected.getStackSize(), Integer.MAX_VALUE);
        if (toDrain <= 0) return 0;

        EnumFacing actualFace = FluidReturnHandler.findFluidOutputFace(te, extractFace, expectedFluid, toDrain);
        if (actualFace == null) return 0;

        FluidStack extracted = FluidReturnHandler.extractFluid(te, actualFace, expectedFluid, toDrain);
        if (extracted == null || extracted.amount <= 0) return 0;

        IAEItemStack toInsert = FluidReturnHandler.packFluid2AEDrops(extracted);
        if (toInsert == null) {
            FluidReturnHandler.fillFluid(te, actualFace, extracted);
            return 0;
        }

        long totalAmount = toInsert.getStackSize();
        IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, this.interfaceRequestSource);

        long insertedAmount = totalAmount;
        if (notInserted != null && notInserted.getStackSize() > 0) {
            insertedAmount -= notInserted.getStackSize();
            FluidStack leftover = new FluidStack(extracted, (int) notInserted.getStackSize());
            FluidReturnHandler.fillFluid(te, actualFace, leftover);
        }

        return insertedAmount;
    }

    @Unique
    private long ae2utilix$extractGas(TileEntity te, EnumFacing extractFace, IAEItemStack expected, IMEInventory<IAEItemStack> dest, IEnergySource energy) {
        String gasName = GasReturnHandler.getGasNameFromAEStack(expected);
        if (gasName == null) return 0;

        int toDraw = (int) Math.min(expected.getStackSize(), Integer.MAX_VALUE);
        if (toDraw <= 0) return 0;

        return MekanismGasHandler.extractAndInsertGas(te, extractFace, gasName, toDraw, dest, energy, this.interfaceRequestSource);
    }

    @Unique
    private final List<IAEItemStack> ae2utilix$expectedResults = new ArrayList<>();

    @Override
    public List<IAEItemStack> ae2utilix$getExpectedResults() {
        return ae2utilix$expectedResults;
    }

    @Override
    public IMEInventory<IAEItemStack> ae2utilix$getStorageInventory() {
        try {
            return this.gridProxy.getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    @Override
    public IEnergySource ae2utilix$getEnergySource() {
        try {
            return this.gridProxy.getEnergy();
        } catch (GridAccessException e) {
            return null;
        }
    }

    @Override
    public IActionSource ae2utilix$getActionSource() {
        return this.interfaceRequestSource;
    }

    @Unique
    private int ae2utilix$cyclesSinceLastCheck = 0;

    @Unique
    private float ae2utilix$currentCycleDelay = 1.0f;

    @Unique
    private static final int AE2UTILIX$MAX_CYCLE_DELAY = 10;

    @Shadow(remap = false)
    public abstract void saveChanges();

    @Shadow(remap = false)
    public abstract int getInstalledUpgrades(Upgrades u);

    @Shadow(remap = false)
    public abstract IItemHandler getInventoryByName(String name);

    @Unique
    private boolean ae2utilix$hasProductReturnCard() {
        IItemHandler upgrades = getInventoryByName("upgrades");
        if (upgrades == null) return false;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemProductReturnCard) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @javax.annotation.Nullable
    private EnumFacing ae2utilix$getPhaseCardFace() {
        IItemHandler upgrades = getInventoryByName("upgrades");
        if (upgrades == null) return null;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemPhaseCard) {
                return ItemPhaseCard.getFace(stack);
            }
        }
        return null;
    }

    @Unique
    private int ae2utilix$magnetTick = 0;

    @Unique
    private boolean ae2utilix$lastMagnetState = false;

    @Unique
    private static final double AE2UTILIX$MAGNET_ENERGY_MULTIPLIER = 10.0;

    @Unique
    private ItemStack ae2utilix$getMagnetCard() {
        IItemHandler upgrades = getInventoryByName("upgrades");
        if (upgrades == null) return ItemStack.EMPTY;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty() && AEApi.instance().definitions().materials().cardMagnet().isSameAs(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private boolean ae2utilix$hasMagnetCard() {
        return !ae2utilix$getMagnetCard().isEmpty();
    }

    @Unique
    private void ae2utilix$doMagnetLogic() {
        ItemStack magnetCard = ae2utilix$getMagnetCard();
        if (magnetCard.isEmpty()) return;

        NBTTagCompound tag = magnetCard.getTagCompound();
        if (tag != null && tag.hasKey("enabled") && !tag.getBoolean("enabled")) return;

        TileEntity tile = this.iHost.getTileEntity();
        if (tile == null) return;
        World world = tile.getWorld();
        if (world == null || world.isRemote) return;

        ae2utilix$magnetTick++;
        if (ae2utilix$magnetTick < 5) return;
        ae2utilix$magnetTick = 0;

        BlockPos pos = tile.getPos();

        appeng.items.contents.CellConfig config = new appeng.items.contents.CellConfig(magnetCard);
        appeng.items.contents.CellUpgrades cellUpgrades = new appeng.items.contents.CellUpgrades(magnetCard, 2);

        boolean isFuzzy = cellUpgrades.getInstalledUpgrades(Upgrades.FUZZY) > 0;
        FuzzyMode fz = null;
        if (isFuzzy) {
            NBTTagCompound magnetTag = Platform.openNbtData(magnetCard);
            if (magnetTag.hasKey("FuzzyMode")) {
                fz = FuzzyMode.valueOf(magnetTag.getString("FuzzyMode"));
            } else {
                fz = FuzzyMode.IGNORE_ALL;
            }
        }
        boolean inverted = cellUpgrades.getInstalledUpgrades(Upgrades.INVERTER) > 0;

        boolean emptyFilter = true;
        for (int ss = 0; ss < config.getSlots(); ss++) {
            if (!config.getStackInSlot(ss).isEmpty()) {
                emptyFilter = false;
                break;
            }
        }

        double range = ae2utilix$getMagnetRange(magnetCard, cellUpgrades);

        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                        pos.getX() + range + 1, pos.getY() + range + 1, pos.getZ() + range + 1));

        try {
            IStorageGrid storage = this.gridProxy.getStorage();
            IMEInventory<IAEItemStack> dest = storage.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IEnergySource energy = this.gridProxy.getEnergy();

            for (EntityItem ei : items) {
                if (ei.isDead) continue;

                NBTTagCompound itemTag = ei.getEntityData();
                if (itemTag.hasKey("PreventRemoteMovement")) continue;

                ItemStack itemStack = ei.getItem();
                if (itemStack.isEmpty()) continue;

                boolean shouldAbsorb = ae2utilix$passesMagnetFilter(magnetCard, itemStack, config, isFuzzy, fz, inverted, emptyFilter);

                if (shouldAbsorb) {
                    IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                    IAEItemStack notInserted = Platform.poweredInsert(energy, dest, aeStack, this.interfaceRequestSource);

                    if (notInserted == null || notInserted.getStackSize() == 0) {
                        ei.setDead();
                    } else {
                        long inserted = aeStack.getStackSize() - notInserted.getStackSize();
                        if (inserted > 0) {
                            itemStack.shrink((int) inserted);
                            if (itemStack.getCount() <= 0) {
                                ei.setDead();
                            }
                        }
                    }
                }
            }
        } catch (GridAccessException ignored) {
        }
    }

    @Unique
    private boolean ae2utilix$passesMagnetFilter(ItemStack magnetCard, ItemStack candidate,
            appeng.items.contents.CellConfig config, boolean isFuzzy, FuzzyMode fz, boolean inverted, boolean emptyFilter) {
        if (BMCCompat.isAvailable() && BMCCompat.hasCustomFilters(magnetCard)) {
            return BMCCompat.passesPickupFilter(magnetCard, candidate);
        }

        boolean matched = false;
        for (int ss = 0; ss < config.getSlots(); ss++) {
            ItemStack filter = config.getStackInSlot(ss);
            if (filter.isEmpty()) continue;
            if (isFuzzy) {
                if (Platform.itemComparisons().isFuzzyEqualItem(filter, candidate, fz)) {
                    matched = true;
                    break;
                }
            } else {
                if (Platform.itemComparisons().isSameItem(filter, candidate)) {
                    matched = true;
                    break;
                }
            }
        }

        return emptyFilter || (matched && !inverted) || (!matched && inverted);
    }

    @Unique
    private double ae2utilix$getMagnetRange(ItemStack magnetCard, IItemHandler cellUpgrades) {
        double base = 5.0;
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("ae2bettermagnetcard")) return base;

        int mult = ae2utilix$getBMCRangeMultiplier(cellUpgrades);
        return base * mult;
    }

    @Unique
    private int ae2utilix$getBMCRangeMultiplier(IItemHandler cellUpgrades) {
        boolean hasRange = false;
        boolean hasAdvanced = false;
        for (int i = 0; i < cellUpgrades.getSlots(); i++) {
            ItemStack up = cellUpgrades.getStackInSlot(i);
            if (up.isEmpty()) continue;
            String typeName = BMCCompat.getTypeName(up);
            if ("ADVANCED_RANGE".equals(typeName)) {
                hasAdvanced = true;
                break;
            } else if ("RANGE".equals(typeName)) {
                hasRange = true;
            }
        }
        if (hasAdvanced) return 3;
        if (hasRange) return 2;
        return 1;
    }

    @Unique
    private EnumFacing ae2utilix$getEffectiveExtractFace(EnumFacing pushDir) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            return effectiveFace != null ? effectiveFace : pushDir.getOpposite();
        }
        EnumFacing phaseFace = ae2utilix$getPhaseCardFace();
        if (phaseFace == null) return pushDir.getOpposite();

        // A card installed by sneak-right-clicking the interface records the
        // interface side facing the target. Inventory capabilities, however,
        // receive the face of the target block itself. Keep cards marked on a
        // target block usable as well by only reversing the adjacent target
        // direction.
        return phaseFace == pushDir ? phaseFace.getOpposite() : phaseFace;
    }

    @Shadow(remap = false)
    private EnumSet<EnumFacing> visitedFaces;

    @Shadow(remap = false)
    private void pushItemsOut(EnumFacing face) {}

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false, require = 0)
    private void ae2utilix$onPushPatternHead(ICraftingPatternDetails patternDetails, InventoryCrafting table, CallbackInfoReturnable<Boolean> cir) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            if (effectiveFace != null) {
                this.visitedFaces = EnumSet.of(effectiveFace.getOpposite());
            }
        }
    }

    @Redirect(method = "pushPattern", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_175625_s(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;", ordinal = 0), remap = false, require = 0)
    private TileEntity ae2utilix$redirectPushPatternGetTile(World world, BlockPos pos) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            BlockPos targetPos = pi.getLinkPos();
            return world.getTileEntity(targetPos);
        }
        return world.getTileEntity(pos);
    }

    @Redirect(method = "pushItemsOut(Lnet/minecraft/util/EnumFacing;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_175625_s(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;", ordinal = 0), remap = false, require = 0)
    private TileEntity ae2utilix$redirectPushItemsOutGetTile(World world, BlockPos pos) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            BlockPos targetPos = pi.getLinkPos();
            return world.getTileEntity(targetPos);
        }
        return world.getTileEntity(pos);
    }

    @Unique
    private boolean ae2utilix$inPushItemsOutRedirect = false;

    @Inject(method = "pushItemsOut(Ljava/util/EnumSet;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ae2utilix$onPushItemsOutSetHead(EnumSet<EnumFacing> possibleDirections, CallbackInfo ci) {
        if (ae2utilix$inPushItemsOutRedirect) return;

        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi == null) return;

        EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
        if (effectiveFace == null) return;

        ci.cancel();

        ae2utilix$inPushItemsOutRedirect = true;
        try {
            this.pushItemsOut(effectiveFace.getOpposite());
        } finally {
            ae2utilix$inPushItemsOutRedirect = false;
        }
    }

    @Inject(method = "isBusy", at = @At(value = "INVOKE", target = "Lappeng/helpers/IInterfaceHost;getTargets()Ljava/util/EnumSet;", remap = false, shift = At.Shift.AFTER), cancellable = true, remap = false, require = 0)
    private void ae2utilix$onIsBusyAfterGetTargets(CallbackInfoReturnable<Boolean> cir) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi == null) return;

        EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
        if (effectiveFace == null) return;

        TileEntity tile = this.iHost.getTileEntity();
        if (tile == null) return;
        World world = tile.getWorld();
        if (world == null) return;

        BlockPos targetPos = pi.getLinkPos();
        TileEntity te = world.getTileEntity(targetPos);
        if (te == null) {
            cir.setReturnValue(true);
            return;
        }

        EnumFacing face = effectiveFace;
        InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, face);
        if (ad != null) {
            boolean busy = !ad.simulateAdd(ItemStack.EMPTY).isEmpty();
            cir.setReturnValue(busy);
        } else {
            cir.setReturnValue(false);
        }
    }

    @ModifyArg(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/util/InventoryAdaptor;getAdaptor(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/EnumFacing;)Lappeng/util/InventoryAdaptor;", remap = false), remap = false, index = 1, require = 0)
    private EnumFacing ae2utilix$modifyPushPatternAdaptorFace(EnumFacing face) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            if (effectiveFace != null) return effectiveFace;
        }
        EnumFacing phaseFace = ae2utilix$getPhaseCardFace();
        return phaseFace != null ? phaseFace : face;
    }

    @ModifyArg(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/implementations/tiles/ICraftingMachine;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/util/EnumFacing;)Z", remap = false), remap = false, index = 2, require = 0)
    private EnumFacing ae2utilix$modifyPushPatternCraftingFace(EnumFacing face) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            if (effectiveFace != null) return effectiveFace;
        }
        EnumFacing phaseFace = ae2utilix$getPhaseCardFace();
        return phaseFace != null ? phaseFace : face;
    }

    @ModifyArg(method = "pushItemsOut(Lnet/minecraft/util/EnumFacing;)V", at = @At(value = "INVOKE", target = "Lappeng/util/InventoryAdaptor;getAdaptor(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/EnumFacing;)Lappeng/util/InventoryAdaptor;", remap = false), remap = false, index = 1, require = 0)
    private EnumFacing ae2utilix$modifyPushItemsOutSingleFace(EnumFacing face) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            if (effectiveFace != null) return effectiveFace;
        }
        EnumFacing phaseFace = ae2utilix$getPhaseCardFace();
        return phaseFace != null ? phaseFace : face;
    }

    @ModifyArg(method = "pushItemsOut(Ljava/util/EnumSet;)V", at = @At(value = "INVOKE", target = "Lappeng/util/InventoryAdaptor;getAdaptor(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/EnumFacing;)Lappeng/util/InventoryAdaptor;", remap = false), remap = false, index = 1, require = 0)
    private EnumFacing ae2utilix$modifyPushItemsOutSetFace(EnumFacing face) {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();
        if (pi != null) {
            EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
            if (effectiveFace != null) return effectiveFace;
        }
        EnumFacing phaseFace = ae2utilix$getPhaseCardFace();
        return phaseFace != null ? phaseFace : face;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"), remap = false, require = 0)
    private void ae2utilix$onPushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (ae2utilix$hasProductReturnCard()) {
            for (IAEItemStack output : patternDetails.getOutputs()) {
                if (output != null) {
                    boolean found = false;
                    for (IAEItemStack existing : ae2utilix$expectedResults) {
                        if (existing.isSameType(output)) {
                            existing.setStackSize(existing.getStackSize() + output.getStackSize());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        ae2utilix$expectedResults.add(output.copy());
                    }
                }
            }

            ae2utilix$cyclesSinceLastCheck = 0;
            ae2utilix$currentCycleDelay = 1.0f;
            this.saveChanges();

            try {
                this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
            } catch (GridAccessException ignored) {
            }
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void ae2utilix$onTickingRequest(IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        boolean hasMagnet = ae2utilix$hasMagnetCard();

        if (hasMagnet != ae2utilix$lastMagnetState) {
            ae2utilix$lastMagnetState = hasMagnet;
            if (hasMagnet) {
                double original = this.gridProxy.getIdlePowerUsage();
                this.gridProxy.setIdlePowerUsage(original * AE2UTILIX$MAGNET_ENERGY_MULTIPLIER);
            } else {
                double current = this.gridProxy.getIdlePowerUsage();
                this.gridProxy.setIdlePowerUsage(current / AE2UTILIX$MAGNET_ENERGY_MULTIPLIER);
            }
        }

        if (hasMagnet) {
            ae2utilix$doMagnetLogic();
        }

        if (ae2utilix$hasProductReturnCard()) {
            if (!ae2utilix$expectedResults.isEmpty()) {
                ae2utilix$cyclesSinceLastCheck++;
                if (ae2utilix$cyclesSinceLastCheck >= (int) ae2utilix$currentCycleDelay) {
                    ae2utilix$cyclesSinceLastCheck = 0;
                    boolean didWork = ae2utilix$doImportWork();

                    if (didWork) {
                        ae2utilix$currentCycleDelay = 1.0f;
                        cir.setReturnValue(TickRateModulation.URGENT);
                    } else {
                        ae2utilix$currentCycleDelay = Math.min(AE2UTILIX$MAX_CYCLE_DELAY, ae2utilix$currentCycleDelay * 1.15f);
                        if (cir.getReturnValue() == TickRateModulation.SLEEP) {
                            cir.setReturnValue(TickRateModulation.SLOWER);
                        }
                    }
                }
            }
        } else {
            if (!ae2utilix$expectedResults.isEmpty()) {
                ae2utilix$expectedResults.clear();
                this.saveChanges();
            }
        }

        if (hasMagnet && cir.getReturnValue() == TickRateModulation.SLEEP) {
            cir.setReturnValue(TickRateModulation.SLOWER);
        }
    }

    @Unique
    private boolean ae2utilix$doImportWork() {
        IPhaseLinkHost pi = ae2utilix$getPhaseInterface();

        if (pi != null) {
            return ae2utilix$doImportWorkPhaseInterface(pi);
        }

        EnumSet<EnumFacing> targets = this.iHost.getTargets();
        if (targets.isEmpty()) return false;

        TileEntity tile = this.iHost.getTileEntity();
        if (tile == null) return false;
        World world = tile.getWorld();
        if (world == null) return false;

        boolean didWork = false;

        try {
            IStorageGrid storage = this.gridProxy.getStorage();
            IMEInventory<IAEItemStack> dest = storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IEnergySource energy = this.gridProxy.getEnergy();

            for (EnumFacing s : targets) {
                TileEntity te = world.getTileEntity(tile.getPos().offset(s));
                if (te == null) continue;

                if (ae2utilix$isP2PTunnel(te, s)) continue;

                EnumFacing extractFace = ae2utilix$getEffectiveExtractFace(s);

                Iterator<IAEItemStack> it = ae2utilix$expectedResults.iterator();
                while (it.hasNext()) {
                    IAEItemStack expected = it.next();
                    long remaining = expected.getStackSize();
                    if (remaining <= 0) {
                        it.remove();
                        continue;
                    }

                    boolean isFluid = FluidReturnHandler.isFluidFakeItem(expected.getDefinition());
                    boolean isGas = !isFluid && GasReturnHandler.hasGasSupport() && GasReturnHandler.isGasFakeItem(expected.getDefinition());

                    long extractedAmount = 0;

                    if (isFluid) {
                        extractedAmount = ae2utilix$extractFluid(te, extractFace, expected, dest, energy);
                    } else if (isGas) {
                        extractedAmount = ae2utilix$extractGas(te, extractFace, expected, dest, energy);
                    } else {
                        int toExtract = (int) Math.min(remaining, expected.getDefinition().getMaxStackSize());
                        if (toExtract > 0) {
                            InventoryAdaptor adaptor = ExtractFaceHelper.findOutputAdaptor(
                                    te, extractFace, expected.getDefinition(), toExtract);
                            if (adaptor != null) {
                                ItemStack extracted = adaptor.removeItems(toExtract, expected.getDefinition(), null);
                                if (!extracted.isEmpty()) {
                                    IAEItemStack toInsert = AEItemStack.fromItemStack(extracted);
                                    IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, this.interfaceRequestSource);
                                    extractedAmount = extracted.getCount();
                                    if (notInserted != null && notInserted.getStackSize() > 0) {
                                        extractedAmount -= notInserted.getStackSize();
                                        adaptor.addItems(notInserted.createItemStack());
                                    }
                                }
                            }
                        }
                    }

                    if (extractedAmount > 0) {
                        long newRemaining = expected.getStackSize() - extractedAmount;
                        if (newRemaining <= 0) {
                            it.remove();
                        } else {
                            expected.setStackSize(newRemaining);
                        }
                        didWork = true;
                    }
                }
            }
        } catch (GridAccessException e) {
            return false;
        }

        if (didWork) {
            this.saveChanges();
        }

        return didWork;
    }

    @Unique
    private boolean ae2utilix$doImportWorkPhaseInterface(IPhaseLinkHost pi) {
        World world = pi.getWorld();
        if (world == null) return false;

        BlockPos targetPos = pi.getLinkPos();
        EnumFacing effectiveFace = ae2utilix$getPhaseInterfaceEffectiveFace();
        if (targetPos == null || effectiveFace == null) return false;

        TileEntity te = world.getTileEntity(targetPos);
        if (te == null) return false;

        boolean didWork = false;

        try {
            IStorageGrid storage = this.gridProxy.getStorage();
            IMEInventory<IAEItemStack> dest = storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IEnergySource energy = this.gridProxy.getEnergy();

            EnumFacing extractFace = effectiveFace;

            Iterator<IAEItemStack> it = ae2utilix$expectedResults.iterator();
            while (it.hasNext()) {
                IAEItemStack expected = it.next();
                long remaining = expected.getStackSize();
                if (remaining <= 0) {
                    it.remove();
                    continue;
                }

                boolean isFluid = FluidReturnHandler.isFluidFakeItem(expected.getDefinition());
                boolean isGas = !isFluid && GasReturnHandler.hasGasSupport() && GasReturnHandler.isGasFakeItem(expected.getDefinition());

                long extractedAmount = 0;

                if (isFluid) {
                    extractedAmount = ae2utilix$extractFluid(te, extractFace, expected, dest, energy);
                } else if (isGas) {
                    extractedAmount = ae2utilix$extractGas(te, extractFace, expected, dest, energy);
                } else {
                    int toExtract = (int) Math.min(remaining, expected.getDefinition().getMaxStackSize());
                    if (toExtract > 0) {
                        InventoryAdaptor adaptor = ExtractFaceHelper.findOutputAdaptor(
                                te, extractFace, expected.getDefinition(), toExtract);
                        if (adaptor != null) {
                            ItemStack extracted = adaptor.removeItems(toExtract, expected.getDefinition(), null);
                            if (!extracted.isEmpty()) {
                                IAEItemStack toInsert = AEItemStack.fromItemStack(extracted);
                                IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, this.interfaceRequestSource);
                                extractedAmount = extracted.getCount();
                                if (notInserted != null && notInserted.getStackSize() > 0) {
                                    extractedAmount -= notInserted.getStackSize();
                                    adaptor.addItems(notInserted.createItemStack());
                                }
                            }
                        }
                    }
                }

                if (extractedAmount > 0) {
                    long newRemaining = expected.getStackSize() - extractedAmount;
                    if (newRemaining <= 0) {
                        it.remove();
                    } else {
                        expected.setStackSize(newRemaining);
                    }
                    didWork = true;
                }
            }
        } catch (GridAccessException e) {
            return false;
        }

        if (didWork) {
            this.saveChanges();
        }

        return didWork;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"), remap = false, require = 0)
    private void ae2utilix$writeToNBT(NBTTagCompound data, CallbackInfo ci) {
        if (!ae2utilix$expectedResults.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (IAEItemStack stack : ae2utilix$expectedResults) {
                NBTTagCompound tag = new NBTTagCompound();
                stack.writeToNBT(tag);
                list.appendTag(tag);
            }
            data.setTag("ae2utilix_expected_results", list);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false, require = 0)
    private void ae2utilix$readFromNBT(NBTTagCompound data, CallbackInfo ci) {
        ae2utilix$expectedResults.clear();
        if (data.hasKey("ae2utilix_expected_results")) {
            NBTTagList list = data.getTagList("ae2utilix_expected_results", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                IAEItemStack stack = AEItemStack.fromNBT(list.getCompoundTagAt(i));
                if (stack != null) {
                    ae2utilix$expectedResults.add(stack);
                }
            }
        }
    }
}
