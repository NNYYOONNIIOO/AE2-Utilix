package com.ae2utilix.mixin;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import co.neeve.nae2.common.parts.p2p.PartP2PInterface;
import com.ae2utilix.IProductReturnHost;
import com.ae2utilix.item.ItemPhaseCard;
import com.ae2utilix.item.ItemProductReturnCard;
import com.ae2utilix.integration.ExtractFaceHelper;
import com.ae2utilix.integration.FluidReturnHandler;
import com.ae2utilix.integration.GasReturnHandler;
import com.ae2utilix.integration.MekanismGasHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

@Mixin(value = PartP2PInterface.class, remap = false)
public abstract class MixinPartP2PInterface {

    @Unique
    private int ae2utilix$consecutiveEmptyTicks = 0;

    @Unique
    private static final int AE2UTILIX$MAX_EMPTY_TICKS = 60;

    @Unique
    private boolean ae2utilix$needsExtract = false;

    @Unique
    @Nullable
    private EnumFacing ae2utilix$getPhaseCardFaceFromSource() {
        PartP2PInterface self = (PartP2PInterface) (Object) this;
        try {
            for (PartP2PInterface input : self.getInputs()) {
                EnumFacing face = ae2utilix$getPhaseCardFaceFromP2P(input);
                if (face != null) return face;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Unique
    private boolean ae2utilix$hasProductReturnCardFromSource() {
        PartP2PInterface self = (PartP2PInterface) (Object) this;
        try {
            for (PartP2PInterface input : self.getInputs()) {
                if (ae2utilix$hasProductReturnCardFromP2P(input)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Unique
    @Nullable
    private IProductReturnHost ae2utilix$findSourceHost() {
        PartP2PInterface self = (PartP2PInterface) (Object) this;
        try {
            for (PartP2PInterface input : self.getInputs()) {
                TileEntity te = input.getTile();
                if (te == null) continue;
                EnumFacing facing = input.getSide().getFacing();
                if (facing == null) continue;
                TileEntity neighbor = te.getWorld().getTileEntity(te.getPos().offset(facing));
                if (neighbor instanceof IInterfaceHost) {
                    IInterfaceHost host = (IInterfaceHost) neighbor;
                    Object duality = host.getInterfaceDuality();
                    if (duality instanceof IProductReturnHost) {
                        return (IProductReturnHost) duality;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Unique
    @Nullable
    private EnumFacing ae2utilix$getPhaseCardFaceFromP2P(PartP2PInterface p2pInput) {
        TileEntity te = p2pInput.getTile();
        if (te == null) return null;
        EnumFacing facing = p2pInput.getSide().getFacing();
        if (facing == null) return null;
        TileEntity neighbor = te.getWorld().getTileEntity(te.getPos().offset(facing));
        if (neighbor instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) neighbor;
            IItemHandler upgrades = host.getInventoryByName("upgrades");
            if (upgrades != null) {
                for (int i = 0; i < upgrades.getSlots(); i++) {
                    ItemStack stack = upgrades.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof ItemPhaseCard) {
                        return ItemPhaseCard.getFace(stack);
                    }
                }
            }
        }
        return null;
    }

    @Unique
    private boolean ae2utilix$hasProductReturnCardFromP2P(PartP2PInterface p2pInput) {
        TileEntity te = p2pInput.getTile();
        if (te == null) return false;
        EnumFacing facing = p2pInput.getSide().getFacing();
        if (facing == null) return false;
        TileEntity neighbor = te.getWorld().getTileEntity(te.getPos().offset(facing));
        if (neighbor instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) neighbor;
            IItemHandler upgrades = host.getInventoryByName("upgrades");
            if (upgrades != null) {
                for (int i = 0; i < upgrades.getSlots(); i++) {
                    ItemStack stack = upgrades.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof ItemProductReturnCard) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @ModifyArg(method = "pushItemsOut", at = @At(value = "INVOKE", target = "Lappeng/util/InventoryAdaptor;getAdaptor(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/util/EnumFacing;)Lappeng/util/InventoryAdaptor;"), index = 1)
    private EnumFacing ae2utilix$modifyPushFace(EnumFacing face) {
        EnumFacing phaseFace = ae2utilix$getPhaseCardFaceFromSource();
        return phaseFace != null ? phaseFace : face;
    }

    @ModifyArg(method = "pushItemsOut", at = @At(value = "INVOKE", target = "Lcom/glodblock/github/inventory/FluidConvertingInventoryAdaptor;wrap(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;Lnet/minecraft/util/EnumFacing;)Lappeng/util/InventoryAdaptor;"), index = 1, require = 0)
    private EnumFacing ae2utilix$modifyPushFaceAE2FC(EnumFacing face) {
        EnumFacing phaseFace = ae2utilix$getPhaseCardFaceFromSource();
        return phaseFace != null ? phaseFace : face;
    }

    @Inject(method = "pushItemsOut", at = @At("TAIL"))
    private void ae2utilix$afterPushItemsOut(CallbackInfoReturnable<Boolean> cir) {
        boolean hasReturn = ae2utilix$hasProductReturnCardFromSource();
        boolean pushed = cir.getReturnValue();
        if (hasReturn && pushed) {
            ae2utilix$needsExtract = true;
            ae2utilix$consecutiveEmptyTicks = 0;
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2utilix$onTickingRequest(IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        boolean hasReturn = ae2utilix$hasProductReturnCardFromSource();
        if (!hasReturn) {
            ae2utilix$consecutiveEmptyTicks = 0;
            ae2utilix$needsExtract = false;
            return;
        }

        if (!ae2utilix$needsExtract) {
            IProductReturnHost sourceHost = ae2utilix$findSourceHost();
            if (sourceHost == null) return;
            List<IAEItemStack> sourceResults = sourceHost.ae2utilix$getExpectedResults();
            if (sourceResults == null || sourceResults.isEmpty()) return;
            ae2utilix$needsExtract = true;
            ae2utilix$consecutiveEmptyTicks = 0;
        }

        if (ae2utilix$consecutiveEmptyTicks >= AE2UTILIX$MAX_EMPTY_TICKS) {
            ae2utilix$needsExtract = false;
            return;
        }

        boolean didWork = ae2utilix$doExtractWork();

        if (didWork) {
            ae2utilix$consecutiveEmptyTicks = 0;
            cir.setReturnValue(TickRateModulation.URGENT);
        } else {
            ae2utilix$consecutiveEmptyTicks++;
            if (cir.getReturnValue() == TickRateModulation.SLEEP) {
                cir.setReturnValue(TickRateModulation.SLOWER);
            }
        }
    }

    @Unique
    private boolean ae2utilix$doExtractWork() {
        PartP2PInterface self = (PartP2PInterface) (Object) this;
        TileEntity tile = self.getTile();
        if (tile == null) return false;
        World world = tile.getWorld();
        if (world == null) return false;

        EnumFacing facing = self.getSide().getFacing();
        if (facing == null) return false;

        TileEntity target = world.getTileEntity(tile.getPos().offset(facing));
        if (target == null) return false;

        EnumFacing extractFace = ae2utilix$getPhaseCardFaceFromSource();
        if (extractFace == null) extractFace = facing.getOpposite();

        IMEInventory<IAEItemStack> dest = null;
        IEnergySource energy = null;
        IActionSource source = null;
        List<IAEItemStack> expectedResults = null;

        IProductReturnHost sourceHost = ae2utilix$findSourceHost();
        if (sourceHost != null) {
            dest = sourceHost.ae2utilix$getStorageInventory();
            energy = sourceHost.ae2utilix$getEnergySource();
            source = sourceHost.ae2utilix$getActionSource();
            expectedResults = sourceHost.ae2utilix$getExpectedResults();
        }

        if (dest == null || energy == null || source == null) {
            try {
                dest = self.getProxy().getStorage().getInventory(
                        AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
                energy = self.getProxy().getEnergy();
                source = new MachineSource(self);
            } catch (GridAccessException e) {
                return false;
            }
        }

        if (dest == null) return false;

        boolean didWork = false;

        if (expectedResults != null && !expectedResults.isEmpty()) {
            Iterator<IAEItemStack> it = expectedResults.iterator();
            while (it.hasNext()) {
                IAEItemStack expected = it.next();
                long remaining = expected.getStackSize();
                if (remaining <= 0) {
                    it.remove();
                    continue;
                }

                boolean isFluid = FluidReturnHandler.hasAE2FC() && FluidReturnHandler.isFluidFakeItem(expected.getDefinition());
                boolean isGas = !isFluid && GasReturnHandler.hasGasSupport() && GasReturnHandler.isGasFakeItem(expected.getDefinition());

                long extractedAmount = 0;

                if (isFluid) {
                    extractedAmount = ae2utilix$extractFluid(target, extractFace, expected, dest, energy, source);
                } else if (isGas) {
                    String gasName = GasReturnHandler.getGasNameFromAEStack(expected);
                    int toDraw = (int) Math.min(remaining, Integer.MAX_VALUE);
                    if (toDraw > 0) {
                        extractedAmount = MekanismGasHandler.extractAndInsertGas(target, extractFace, gasName, toDraw, dest, energy, source);
                    }
                } else {
                    int toExtract = (int) Math.min(remaining, expected.getDefinition().getMaxStackSize());
                    if (toExtract > 0) {
                        InventoryAdaptor adaptor = ExtractFaceHelper.findOutputAdaptor(
                                target, extractFace, expected.getDefinition(), toExtract);
                        if (adaptor != null) {
                            ItemStack simulated = adaptor.simulateRemove(toExtract, expected.getDefinition(), null);
                            if (!simulated.isEmpty()) {
                                IAEItemStack simToInsert = AEItemStack.fromItemStack(simulated);
                                if (simToInsert != null) {
                                    IAEItemStack simNotInserted = dest.injectItems(simToInsert.copy(), Actionable.SIMULATE, source);
                                    long canAccept = simToInsert.getStackSize() - (simNotInserted != null ? simNotInserted.getStackSize() : 0);
                                    if (canAccept > 0) {
                                        int actualExtract = (int) Math.min(canAccept, simulated.getCount());
                                        ItemStack extracted = adaptor.removeItems(actualExtract, expected.getDefinition(), null);
                                        if (!extracted.isEmpty()) {
                                            IAEItemStack toInsert = AEItemStack.fromItemStack(extracted);
                                            IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, source);
                                            extractedAmount = extracted.getCount();
                                            if (notInserted != null && notInserted.getStackSize() > 0) {
                                                extractedAmount -= notInserted.getStackSize();
                                                adaptor.addItems(notInserted.createItemStack());
                                            }
                                        }
                                    }
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

            if (expectedResults.isEmpty()) {
                ae2utilix$needsExtract = false;
            }
        } else {
            didWork = ae2utilix$extractAnyItems(target, dest, energy, source);

            if (!didWork && FluidReturnHandler.hasAE2FC()) {
                didWork = ae2utilix$extractFluids(target, extractFace, dest, energy, source);
            }

            if (!didWork && GasReturnHandler.hasGasSupport()) {
                didWork = ae2utilix$extractGases(target, extractFace, dest, energy, source);
            }
        }

        return didWork;
    }

    @Unique
    private boolean ae2utilix$extractAnyItems(TileEntity target, IMEInventory<IAEItemStack> dest, IEnergySource energy, IActionSource source) {
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = ExtractFaceHelper.getItemHandler(target, face);
            if (handler == null) continue;
            boolean didWork = ae2utilix$extractAnyFromHandler(handler, dest, energy, source);
            if (didWork) return true;
        }

        IItemHandler nullHandler = ExtractFaceHelper.getItemHandler(target, null);
        if (nullHandler != null) {
            return ae2utilix$extractAnyFromHandler(nullHandler, dest, energy, source);
        }

        return false;
    }

    @Unique
    private boolean ae2utilix$extractAnyFromHandler(IItemHandler handler, IMEInventory<IAEItemStack> dest, IEnergySource energy, IActionSource source) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack simulated = handler.extractItem(i, stack.getCount(), true);
            if (simulated.isEmpty()) continue;

            IAEItemStack simToInsert = AEItemStack.fromItemStack(simulated);
            if (simToInsert == null) continue;

            IAEItemStack simNotInserted = dest.injectItems(simToInsert.copy(), Actionable.SIMULATE, source);
            long canAccept = simToInsert.getStackSize() - (simNotInserted != null ? simNotInserted.getStackSize() : 0);
            if (canAccept <= 0) continue;

            int actualExtract = (int) Math.min(canAccept, simulated.getCount());
            ItemStack extracted = handler.extractItem(i, actualExtract, false);
            if (extracted.isEmpty()) continue;

            IAEItemStack toInsert = AEItemStack.fromItemStack(extracted);
            IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, source);
            if (notInserted != null && notInserted.getStackSize() > 0) {
                ae2utilix$insertToHandler(handler, notInserted.createItemStack());
            }
            return true;
        }
        return false;
    }

    @Unique
    private long ae2utilix$extractFluid(TileEntity te, EnumFacing extractFace, IAEItemStack expected, IMEInventory<IAEItemStack> dest, IEnergySource energy, IActionSource source) {
        net.minecraftforge.fluids.FluidStack expectedFluid = FluidReturnHandler.getFluidFromAEStack(expected);
        if (expectedFluid == null) return 0;

        int toDrain = (int) Math.min(expected.getStackSize(), Integer.MAX_VALUE);
        if (toDrain <= 0) return 0;

        EnumFacing actualFace = FluidReturnHandler.findFluidOutputFace(te, extractFace, expectedFluid, toDrain);
        if (actualFace == null) return 0;

        net.minecraftforge.fluids.FluidStack extracted = FluidReturnHandler.extractFluid(te, actualFace, expectedFluid, toDrain);
        if (extracted == null || extracted.amount <= 0) return 0;

        IAEItemStack toInsert = FluidReturnHandler.packFluid2AEDrops(extracted);
        if (toInsert == null) {
            FluidReturnHandler.fillFluid(te, actualFace, extracted);
            return 0;
        }

        long totalAmount = toInsert.getStackSize();
        IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, source);

        long insertedAmount = totalAmount;
        if (notInserted != null && notInserted.getStackSize() > 0) {
            insertedAmount -= notInserted.getStackSize();
            net.minecraftforge.fluids.FluidStack leftover = new net.minecraftforge.fluids.FluidStack(extracted, (int) notInserted.getStackSize());
            FluidReturnHandler.fillFluid(te, actualFace, leftover);
        }

        return insertedAmount;
    }

    @Unique
    private boolean ae2utilix$extractFluids(TileEntity te, EnumFacing extractFace,
                                             IMEInventory<IAEItemStack> dest, IEnergySource energy, IActionSource source) {
        boolean didWork = false;
        net.minecraftforge.fluids.capability.IFluidHandler fluidHandler = te.getCapability(
                net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, extractFace);
        if (fluidHandler != null) {
            for (int attempt = 0; attempt < 4; attempt++) {
                net.minecraftforge.fluids.FluidStack simulated = fluidHandler.drain(1000, false);
                if (simulated == null || simulated.amount <= 0) break;
                net.minecraftforge.fluids.FluidStack extracted = fluidHandler.drain(simulated.amount, true);
                if (extracted == null || extracted.amount <= 0) break;
                IAEItemStack toInsert = FluidReturnHandler.packFluid2AEDrops(extracted);
                if (toInsert == null) {
                    fluidHandler.fill(extracted, true);
                    break;
                }
                long totalAmount = toInsert.getStackSize();
                IAEItemStack notInserted = Platform.poweredInsert(energy, dest, toInsert, source);
                long insertedAmount = totalAmount;
                if (notInserted != null && notInserted.getStackSize() > 0) {
                    insertedAmount -= notInserted.getStackSize();
                    net.minecraftforge.fluids.FluidStack leftover = new net.minecraftforge.fluids.FluidStack(extracted, (int) notInserted.getStackSize());
                    fluidHandler.fill(leftover, true);
                }
                if (insertedAmount > 0) didWork = true;
            }
        }
        return didWork;
    }

    @Unique
    private boolean ae2utilix$extractGases(TileEntity te, EnumFacing extractFace,
                                           IMEInventory<IAEItemStack> dest, IEnergySource energy, IActionSource source) {
        return MekanismGasHandler.extractAllGases(te, extractFace, dest, energy, source);
    }

    @Unique
    private void ae2utilix$insertToHandler(IItemHandler handler, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack leftover = handler.insertItem(i, stack, false);
            if (leftover.isEmpty()) return;
            stack = leftover;
        }
    }
}
