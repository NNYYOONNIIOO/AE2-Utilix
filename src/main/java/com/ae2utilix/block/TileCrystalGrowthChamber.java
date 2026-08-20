package com.ae2utilix.block;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.definitions.IMaterials;
import appeng.me.GridAccessException;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.InvOperation;
import com.ae2utilix.recipe.CrystalGrowthRecipe;
import com.ae2utilix.recipe.CrystalGrowthRecipes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumSet;

public class TileCrystalGrowthChamber extends AENetworkPowerTile implements IGridTickable {

    private static final double MAX_POWER = 100000.0;
    private static final double EXTRACT_RATE = 8000.0;
    private static final double SPEED_CARD_MULTIPLIER = 0.4;
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 6;
    public static final int UPGRADE_SLOTS = 5;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, INPUT_SLOTS);
    private final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, OUTPUT_SLOTS);
    private final AppEngInternalInventory upgradeInv = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1);

    private boolean powered = false;
    private boolean ejecting = false;
    private final boolean[] faceEjectConfig = new boolean[6];

    private int progress = 0;
    private int maxProgress = 100;
    private CrystalGrowthRecipe currentRecipe = null;
    private int currentParallelRuns = 0;
    private ItemStack clientDisplayStack = ItemStack.EMPTY;

    private final NotifyingFluidTank inputTank = new NotifyingFluidTank(5000);
    private final NotifyingFluidTank outputTank = new NotifyingFluidTank(5000);
    private final CombinedFluidHandler combinedFluidHandler = new CombinedFluidHandler();

    public TileCrystalGrowthChamber() {
        this.getProxy().setFlags();
        this.setInternalMaxPower(MAX_POWER);
        this.setInternalPublicPowerStorage(false);
        this.getProxy().setIdlePowerUsage(0);
        this.getProxy().setValidSides(EnumSet.noneOf(EnumFacing.class));
    }

    @Override
    public void onReady() {
        super.onReady();
        this.updateValidSides();
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("ejecting", ejecting);
        tag.setByteArray("faceEject", packBooleans(faceEjectConfig));
        tag.setInteger("progress", progress);
        tag.setInteger("maxProgress", maxProgress);
        tag.setTag("inputTank", inputTank.writeToNBT(new NBTTagCompound()));
        tag.setTag("outputTank", outputTank.writeToNBT(new NBTTagCompound()));
        if (currentRecipe != null && !currentRecipe.getOutputs().isEmpty()) {
            NBTTagCompound recipeOutput = new NBTTagCompound();
            currentRecipe.getOutputs().get(0).writeToNBT(recipeOutput);
            tag.setTag("recipeOutput", recipeOutput);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        ejecting = tag.getBoolean("ejecting");
        if (tag.hasKey("faceEject")) {
            unpackBooleans(tag.getByteArray("faceEject"), faceEjectConfig);
        }
        progress = tag.getInteger("progress");
        maxProgress = tag.hasKey("maxProgress") ? tag.getInteger("maxProgress") : 100;
        if (tag.hasKey("inputTank")) {
            inputTank.readFromNBT(tag.getCompoundTag("inputTank"));
        }
        if (tag.hasKey("outputTank")) {
            outputTank.readFromNBT(tag.getCompoundTag("outputTank"));
        }
        if (tag.hasKey("recipeOutput")) {
            clientDisplayStack = new ItemStack(tag.getCompoundTag("recipeOutput"));
        } else {
            clientDisplayStack = ItemStack.EMPTY;
        }
    }

    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    private void syncToClients() {
        if (world != null && !world.isRemote) {
            WorldServer ws = (WorldServer) world;
            SPacketUpdateTileEntity packet = getUpdatePacket();
            if (packet != null) {
                PlayerChunkMapEntry entry = ws.getPlayerChunkMap().getEntry(pos.getX() >> 4, pos.getZ() >> 4);
                if (entry != null) {
                    entry.sendPacket(packet);
                }
            }
            ws.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public void setOrientation(EnumFacing inForward, EnumFacing inUp) {
        super.setOrientation(inForward, inUp);
        if (this.world != null) {
            this.updateValidSides();
        }
    }

    private void updateValidSides() {
        if (world != null && world.isRemote) return;
        EnumFacing forward = this.getForward();
        if (forward == null) return;
        EnumSet<EnumFacing> validSides = EnumSet.of(forward, forward.getOpposite(), EnumFacing.DOWN);
        this.getProxy().setValidSides(validSides);
        this.setPowerSides(validSides);
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.COVERED;
    }

    public void activate(EntityPlayer player) {
        syncToClients();
        player.openGui(com.ae2utilix.AE2Utilix.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean dropItems() {
        World world = this.getWorld();
        BlockPos pos = this.getPos();
        for (int i = 0; i < inputInv.getSlots(); i++) {
            ItemStack stack = inputInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        for (int i = 0; i < outputInv.getSlots(); i++) {
            ItemStack stack = outputInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            ItemStack stack = upgradeInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        return super.dropItems();
    }

    public IItemHandler getInputInv() { return inputInv; }
    public IItemHandler getOutputInv() { return outputInv; }
    public IItemHandler getUpgradeInv() { return upgradeInv; }
    public CrystalGrowthRecipe getCurrentRecipe() { return currentRecipe; }
    public ItemStack getClientDisplayStack() { return clientDisplayStack; }

    @Override
    public IItemHandler getInternalInventory() {
        return new CombinedInvWrapper(inputInv, outputInv, upgradeInv);
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        markDirty();
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException ignored) {
        }
    }

    public boolean isPowered() { return powered; }
    public void setPowered(boolean powered) {
        this.powered = powered;
        markDirty();
        syncToClients();
    }

    public boolean isEjecting() { return ejecting; }
    public void setEjecting(boolean ejecting) {
        this.ejecting = ejecting;
        markDirty();
        syncToClients();
    }

    public int getProgress() { return progress; }
    public void setProgress(int progress) {
        this.progress = progress;
        markDirty();
        syncToClients();
    }

    public int getMaxProgress() { return maxProgress; }
    public void setMaxProgress(int maxProgress) {
        this.maxProgress = maxProgress;
        markDirty();
        syncToClients();
    }

    public EnumFacing guiIndexToEnumFacing(int guiIndex) {
        EnumFacing forward = getForward();
        if (forward == null) return null;
        switch (guiIndex) {
            case 0: return EnumFacing.UP;
            case 1: return forward.rotateY();
            case 2: return forward;
            case 3: return forward.rotateYCCW();
            case 4: return EnumFacing.DOWN;
            case 5: return forward.getOpposite();
            default: return null;
        }
    }

    public int enumFacingToGuiIndex(EnumFacing facing) {
        EnumFacing forward = getForward();
        if (forward == null) return -1;
        if (facing == EnumFacing.UP) return 0;
        if (facing == forward.rotateY()) return 1;
        if (facing == forward) return 2;
        if (facing == forward.rotateYCCW()) return 3;
        if (facing == EnumFacing.DOWN) return 4;
        if (facing == forward.getOpposite()) return 5;
        return -1;
    }

    public boolean isFaceEjecting(int faceIndex) {
        EnumFacing facing = guiIndexToEnumFacing(faceIndex);
        if (facing == null) return false;
        return faceEjectConfig[facing.getIndex()];
    }

    public void setFaceEjecting(int faceIndex, boolean value) {
        EnumFacing facing = guiIndexToEnumFacing(faceIndex);
        if (facing != null) {
            faceEjectConfig[facing.getIndex()] = value;
            markDirty();
            syncToClients();
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(combinedFluidHandler);
        }
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            @SuppressWarnings("unchecked")
            IItemHandler base = (IItemHandler) super.getCapability(capability, facing);
            if (base != null) {
                return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(
                        new ExternalItemHandler(base));
            }
            return null;
        }
        return super.getCapability(capability, facing);
    }

    public FluidStack getInputFluid() { return inputTank.getFluid(); }
    public void setInputFluid(FluidStack fluid) {
        if (fluid != null && fluid.amount > inputTank.getCapacity()) {
            fluid = new FluidStack(fluid, inputTank.getCapacity());
        }
        inputTank.setFluid(fluid);
        markDirty();
        syncToClients();
    }

    public FluidStack getOutputFluid() { return outputTank.getFluid(); }
    public void setOutputFluid(FluidStack fluid) {
        if (fluid != null && fluid.amount > outputTank.getCapacity()) {
            fluid = new FluidStack(fluid, outputTank.getCapacity());
        }
        outputTank.setFluid(fluid);
        markDirty();
        syncToClients();
    }

    public FluidTank getInputTank() { return inputTank; }
    public FluidTank getOutputTank() { return outputTank; }

    public int getMaxFluidMB() { return 5000; }

    public int getSpeedCardCount() {
        int count = 0;
        IMaterials materials = appeng.api.AEApi.instance().definitions().materials();
        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            ItemStack stack = upgradeInv.getStackInSlot(i);
            if (!stack.isEmpty() && materials.cardSpeed().isSameAs(stack)) {
                count++;
            }
        }
        return count;
    }

    public int getParallelCardCount() {
        int count = 0;
        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            ItemStack stack = upgradeInv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof com.ae2utilix.item.ItemParallelCard) {
                count++;
            }
        }
        return count;
    }

    public int getAdjustedProcessingTime(int baseTime) {
        int cards = getSpeedCardCount();
        if (cards <= 0) return baseTime;
        double adjusted = baseTime * Math.pow(SPEED_CARD_MULTIPLIER, cards);
        return Math.max(1, (int) Math.round(adjusted));
    }

    public double getAdjustedEnergyPerTick(double totalEnergy, int baseTime) {
        int adjustedTime = getAdjustedProcessingTime(baseTime);
        return adjustedTime > 0 ? totalEnergy / adjustedTime : 0;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 40, false, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        // Keep initialization lazy so all item and fluid registries are ready.
        CrystalGrowthRecipes.init();
        if (this.getInternalCurrentPower() < this.getInternalMaxPower()) {
            try {
                double demand = Math.min(EXTRACT_RATE, this.getInternalMaxPower() - this.getInternalCurrentPower());
                double extracted = this.getProxy().getEnergy().extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.ONE);
                if (extracted > 0) {
                    this.injectAEPower(extracted, Actionable.MODULATE);
                }
            } catch (GridAccessException e) {
                return TickRateModulation.SLOWER;
            }
        }

        if (ejecting) {
            ejectItems();
        }

        if (currentRecipe != null) {
            return processRecipe(ticksSinceLastCall);
        } else {
            return tryStartRecipe();
        }
    }

    private void ejectItems() {
        for (EnumFacing facing : EnumFacing.values()) {
            if (!faceEjectConfig[facing.getIndex()]) continue;

            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos.offset(facing));
            if (te == null) continue;

            IItemHandler targetInv = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            if (targetInv != null) {
                for (int i = 0; i < outputInv.getSlots(); i++) {
                    ItemStack stack = outputInv.getStackInSlot(i);
                    if (stack.isEmpty()) continue;

                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(targetInv, stack, true);
                    int toExtract = stack.getCount() - remaining.getCount();
                    if (toExtract <= 0) continue;

                    ItemStack extracted = outputInv.extractItem(i, toExtract, false);
                    if (!extracted.isEmpty()) {
                        ItemHandlerHelper.insertItemStacked(targetInv, extracted, false);
                    }
                }
            }

            net.minecraftforge.fluids.capability.IFluidHandler targetTank = te.getCapability(
                    net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing.getOpposite());
            if (targetTank != null && outputTank.getFluid() != null && outputTank.getFluidAmount() > 0) {
                FluidStack toDrain = outputTank.getFluid().copy();
                int filled = targetTank.fill(toDrain, true);
                if (filled > 0) {
                    outputTank.drain(filled, true);
                }
            }
        }
    }

    private TickRateModulation tryStartRecipe() {
        int maxParallelRuns = 1 << getParallelCardCount();
        int parallelRuns = 0;
        CrystalGrowthRecipe recipe = null;
        // Parallel cards provide a maximum, not a mandatory batch size. Try
        // the largest batch that the available inputs and output capacity can
        // currently support, then fall back to a smaller batch.
        for (int candidateRuns = maxParallelRuns; candidateRuns >= 1; candidateRuns--) {
            CrystalGrowthRecipe candidate = CrystalGrowthRecipes.findMatchingRecipe(
                    inputInv, inputTank.getFluid(), candidateRuns);
            if (candidate == null
                    || !candidate.canFitOutputs(outputInv, candidateRuns)
                    || !candidate.canFitFluidOutput(outputTank, candidateRuns)) {
                continue;
            }
            recipe = candidate;
            parallelRuns = candidateRuns;
            break;
        }
        if (recipe == null) {
            if (progress > 0) {
                progress = 0;
                maxProgress = 100;
                syncToClients();
            }
            return TickRateModulation.SLOWER;
        }

        double energyPerTick = getAdjustedEnergyPerTick(recipe.getEnergyCost(), recipe.getProcessingTime()) * parallelRuns;
        if (this.getInternalCurrentPower() < energyPerTick) {
            return TickRateModulation.SLOWER;
        }

        currentRecipe = recipe;
        currentParallelRuns = parallelRuns;
        maxProgress = getAdjustedProcessingTime(recipe.getProcessingTime());
        progress = 0;

        if (recipe.getInputFluid() != null) {
            FluidStack drainFluid = recipe.getInputFluid().copy();
            drainFluid.amount *= parallelRuns;
            inputTank.drain(drainFluid, true);
        }

        return TickRateModulation.FASTER;
    }

    private TickRateModulation processRecipe(int ticksSinceLastCall) {
        int parallelRuns = Math.max(1, currentParallelRuns);
        double energyPerTick = getAdjustedEnergyPerTick(currentRecipe.getEnergyCost(), currentRecipe.getProcessingTime()) * parallelRuns;

        if (this.getInternalCurrentPower() < energyPerTick) {
            return TickRateModulation.SLOWER;
        }

        this.extractAEPower(energyPerTick, Actionable.MODULATE);

        progress += ticksSinceLastCall;

        if (progress >= maxProgress) {
            if (currentRecipe.canFitOutputs(outputInv, parallelRuns) && currentRecipe.canFitFluidOutput(outputTank, parallelRuns)) {
                for (int i = 0; i < parallelRuns; i++) {
                    currentRecipe.consumeInputs(inputInv);
                }
                for (int i = 0; i < parallelRuns; i++) {
                    currentRecipe.produceOutputs(outputInv);
                    currentRecipe.produceFluidOutput(outputTank);
                }
                markDirty();
                syncToClients();
            }
            currentRecipe = null;
            currentParallelRuns = 0;
            progress = 0;
            maxProgress = 100;
            TickRateModulation result = tryStartRecipe();
            return result == TickRateModulation.IDLE ? TickRateModulation.URGENT : result;
        }

        syncToClients();
        return TickRateModulation.FASTER;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("inputInv", this.inputInv.serializeNBT());
        data.setTag("outputInv", this.outputInv.serializeNBT());
        data.setTag("upgradeInv", this.upgradeInv.serializeNBT());
        data.setBoolean("powered", powered);
        data.setBoolean("ejecting", ejecting);
        data.setByteArray("faceEject", packBooleans(faceEjectConfig));
        data.setInteger("progress", progress);
        data.setInteger("maxProgress", maxProgress);
        data.setTag("inputTank", inputTank.writeToNBT(new NBTTagCompound()));
        data.setTag("outputTank", outputTank.writeToNBT(new NBTTagCompound()));
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("inputInv")) {
            this.inputInv.deserializeNBT(data.getCompoundTag("inputInv"));
        }
        if (data.hasKey("outputInv")) {
            this.outputInv.deserializeNBT(data.getCompoundTag("outputInv"));
        }
        if (data.hasKey("upgradeInv")) {
            this.upgradeInv.deserializeNBT(data.getCompoundTag("upgradeInv"));
        }
        powered = data.getBoolean("powered");
        ejecting = data.getBoolean("ejecting");
        if (data.hasKey("faceEject")) {
            unpackBooleans(data.getByteArray("faceEject"), faceEjectConfig);
        }
        progress = data.getInteger("progress");
        maxProgress = data.hasKey("maxProgress") ? data.getInteger("maxProgress") : 100;
        if (data.hasKey("inputTank")) {
            inputTank.readFromNBT(data.getCompoundTag("inputTank"));
        } else if (data.hasKey("inputFluid")) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(data.getCompoundTag("inputFluid"));
            if (fluid != null) {
                if (fluid.amount > inputTank.getCapacity()) {
                    fluid.amount = inputTank.getCapacity();
                }
                inputTank.setFluid(fluid);
            }
        }
        if (data.hasKey("outputTank")) {
            outputTank.readFromNBT(data.getCompoundTag("outputTank"));
        } else if (data.hasKey("outputFluid")) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(data.getCompoundTag("outputFluid"));
            if (fluid != null) {
                if (fluid.amount > outputTank.getCapacity()) {
                    fluid.amount = outputTank.getCapacity();
                }
                outputTank.setFluid(fluid);
            }
        }
        this.updateValidSides();
    }

    private static byte[] packBooleans(boolean[] values) {
        byte[] packed = new byte[(values.length + 7) / 8];
        for (int i = 0; i < values.length; i++) {
            if (values[i]) packed[i / 8] |= (byte) (1 << (i % 8));
        }
        return packed;
    }

    private static void unpackBooleans(byte[] packed, boolean[] values) {
        int len = Math.min(values.length, packed.length * 8);
        for (int i = 0; i < len; i++) {
            values[i] = ((packed[i / 8] >> (i % 8)) & 1) == 1;
        }
    }

    private class CombinedInvWrapper implements IItemHandler {
        private final IItemHandler[] handlers;

        CombinedInvWrapper(IItemHandler... handlers) {
            this.handlers = handlers;
        }

        @Override
        public int getSlots() {
            int total = 0;
            for (IItemHandler h : handlers) total += h.getSlots();
            return total;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int index = slot;
            for (IItemHandler h : handlers) {
                if (index < h.getSlots()) return h.getStackInSlot(index);
                index -= h.getSlots();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int index = slot;
            for (IItemHandler h : handlers) {
                if (index < h.getSlots()) return h.insertItem(index, stack, simulate);
                index -= h.getSlots();
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int index = slot;
            for (IItemHandler h : handlers) {
                if (index < h.getSlots()) return h.extractItem(index, amount, simulate);
                index -= h.getSlots();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int index = slot;
            for (IItemHandler h : handlers) {
                if (index < h.getSlots()) return h.getSlotLimit(index);
                index -= h.getSlots();
            }
            return 0;
        }
    }

    private class ExternalItemHandler implements IItemHandler {
        private final IItemHandler internal;

        ExternalItemHandler(IItemHandler internal) {
            this.internal = internal;
        }

        @Override
        public int getSlots() {
            return internal.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return internal.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUT_SLOTS) {
                return stack;
            }
            return internal.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return internal.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return internal.getSlotLimit(slot);
        }
    }

    private class NotifyingFluidTank extends FluidTank {
        NotifyingFluidTank(int capacity) {
            super(capacity);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            int filled = super.fill(resource, doFill);
            if (filled > 0 && doFill) {
                markDirty();
                syncToClients();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            FluidStack drained = super.drain(resource, doDrain);
            if (drained != null && drained.amount > 0 && doDrain) {
                markDirty();
                syncToClients();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = super.drain(maxDrain, doDrain);
            if (drained != null && drained.amount > 0 && doDrain) {
                markDirty();
                syncToClients();
            }
            return drained;
        }
    }

    private class CombinedFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] inputProps = inputTank.getTankProperties();
            IFluidTankProperties[] outputProps = outputTank.getTankProperties();
            IFluidTankProperties[] all = new IFluidTankProperties[inputProps.length + outputProps.length];
            System.arraycopy(inputProps, 0, all, 0, inputProps.length);
            System.arraycopy(outputProps, 0, all, inputProps.length, outputProps.length);
            return all;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return inputTank.fill(resource, doFill);
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return outputTank.drain(resource, doDrain);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return outputTank.drain(maxDrain, doDrain);
        }
    }
}
