package com.ae2utilix.ae2_utilix.block;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.AECableType;
import ae2.core.definitions.AEItems;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.CombinedInternalInventory;
import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipe;
import com.ae2utilix.ae2_utilix.recipe.CrystalGrowthRecipes;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.EnumSet;

/**
 * Crystal growth chamber: consumes items plus optional fluid and AE power to grow
 * crystals. Ported from the Forge build, adapted to Supergiant's tile/inventory API.
 */
public class TileCrystalGrowthChamber extends AENetworkedPoweredTile
        implements IGridTickable, IUpgradeableObject {

    private static final double MAX_POWER = 100000.0;
    private static final double EXTRACT_RATE = 8000.0;
    private static final double SPEED_CARD_MULTIPLIER = 0.4;
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 6;
    public static final int UPGRADE_SLOTS = 5;
    /** Cap on parallel cards, so the batch size stays bounded. */
    public static final int MAX_PARALLEL_CARDS = 5;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, INPUT_SLOTS);
    private final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, OUTPUT_SLOTS);
    /**
     * AE2's own machine upgrade inventory.
     *
     * <p>Using this instead of a plain inventory is what makes AE2 accept the cards this
     * machine declares in {@code Upgrades}, and it is also the source of the card tooltip
     * list. A plain inventory accepts any item and knows nothing about the associations.</p>
     */
    private final IUpgradeInventory upgradeInv = UpgradeInventories.forMachine(
            net.minecraft.item.Item.getItemFromBlock(
                    com.ae2utilix.ae2_utilix.AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER),
            UPGRADE_SLOTS, this::saveChanges);
    private final InternalInventory combinedInv =
            new CombinedInternalInventory(this.inputInv, this.outputInv, this.upgradeInv);
    private final InternalInventory externalInv = new ExternalItemHandler();

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
        this.setInternalMaxPower(MAX_POWER);
        this.setInternalPublicPowerStorage(false);
        this.setPowerSides(EnumSet.allOf(EnumFacing.class));
        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public net.minecraft.item.ItemStack getItemFromTile() {
        return new net.minecraft.item.ItemStack(
                net.minecraft.item.Item.getItemFromBlock(com.ae2utilix.ae2_utilix.AE2Utilix.BLOCK_CRYSTAL_GROWTH_CHAMBER));
    }

    public void activate(EntityPlayer player) {
        this.markForUpdate();
        player.openGui(com.ae2utilix.ae2_utilix.AE2Utilix.instance(), 0, world, pos.getX(), pos.getY(), pos.getZ());
    }

    // ---------------------------------------------------------------- inventories

    public IItemHandler getInputInv() {
        return this.inputInv.toItemHandler();
    }

    public IItemHandler getOutputInv() {
        return this.outputInv.toItemHandler();
    }

    public InternalInventory getUpgradeInventory() {
        return this.upgradeInv;
    }

    public IItemHandler getUpgradeInv() {
        return this.upgradeInv.toItemHandler();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgradeInv;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.combinedInv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.externalInv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChanges();
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public boolean isClientSide() {
        return this.world != null && this.world.isRemote;
    }

    // ------------------------------------------------------------------- NBT I/O

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("inputInv")) {
            this.inputInv.readFromNBT(data, "inputInv");
        }
        if (data.hasKey("outputInv")) {
            this.outputInv.readFromNBT(data, "outputInv");
        }
        if (data.hasKey("upgradeInv")) {
            this.upgradeInv.readFromNBT(data, "upgradeInv");
        }
        this.powered = data.getBoolean("powered");
        this.ejecting = data.getBoolean("ejecting");
        if (data.hasKey("faceEject")) {
            unpackBooleans(data.getByteArray("faceEject"), this.faceEjectConfig);
        }
        // Tanks must be restored before the batch is validated: isFluidEqual and the
        // quantity check both read the input tank, and an empty tank would reject every
        // fluid-using recipe and silently throw the running batch away.
        if (data.hasKey("inputTank")) {
            this.inputTank.readFromNBT(data.getCompoundTag("inputTank"));
        }
        if (data.hasKey("outputTank")) {
            this.outputTank.readFromNBT(data.getCompoundTag("outputTank"));
        }

        // Resume the batch that was running when the world was saved. The recipe travels in
        // its own tag, so the chamber continues from the saved progress instead of giving
        // the batch up or restarting it.
        this.currentRecipe = data.hasKey("activeRecipe", 10)
                ? CrystalGrowthRecipe.readFromNBT(data.getCompoundTag("activeRecipe"))
                : null;
        this.currentParallelRuns = data.hasKey("activeParallel") ? data.getInteger("activeParallel") : 0;
        this.progress = data.hasKey("activeProgress") ? data.getInteger("activeProgress") : 0;
        this.maxProgress = data.hasKey("activeMaxProgress") ? data.getInteger("activeMaxProgress") : 100;

        // A batch whose inputs were removed while the world was unloaded cannot complete.
        if (this.currentRecipe != null && !this.currentRecipe.matches(
                this.inputInv.toItemHandler(), this.inputTank.getFluid(), Math.max(1, this.currentParallelRuns))) {
            this.currentRecipe = null;
            this.currentParallelRuns = 0;
            this.progress = 0;
            this.maxProgress = 100;
        }
        if (com.ae2utilix.ae2_utilix.config.AE2UtilixConfig.debugCrystalGrowth) {
            com.ae2utilix.ae2_utilix.AE2Utilix.LOGGER.info(
                    "CGC load at {}: recipe={} progress={}/{} parallel={} inputs={} fluid={}",
                    this.pos, this.currentRecipe != null, this.progress, this.maxProgress, this.currentParallelRuns,
                    describeInputs(), this.inputTank.getFluid());
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        // A chamber that was saved while a batch was running has to keep ticking without a
        // manual block update. Supergiant puts the restored node back into its tick queue on
        // its own, but waking it here also covers nodes that came back as sleeping, which is
        // what left the chamber frozen at progress 0 until its cable was reconnected.
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.inputInv.writeToNBT(data, "inputInv");
        this.outputInv.writeToNBT(data, "outputInv");
        this.upgradeInv.writeToNBT(data, "upgradeInv");
        data.setBoolean("powered", this.powered);
        data.setBoolean("ejecting", this.ejecting);
        data.setByteArray("faceEject", packBooleans(this.faceEjectConfig));
        if (this.currentRecipe != null) {
            data.setTag("activeRecipe", this.currentRecipe.writeToNBT(new NBTTagCompound()));
            data.setInteger("activeParallel", this.currentParallelRuns);
            data.setInteger("activeProgress", this.progress);
            data.setInteger("activeMaxProgress", this.maxProgress);
        }
        data.setTag("inputTank", this.inputTank.writeToNBT(new NBTTagCompound()));
        data.setTag("outputTank", this.outputTank.writeToNBT(new NBTTagCompound()));
    }

    // ------------------------------------------------------------- state transfers

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
        this.saveChanges();
        this.markForUpdate();
    }

    public boolean isEjecting() {
        return this.ejecting;
    }

    public void setEjecting(boolean ejecting) {
        this.ejecting = ejecting;
        this.saveChanges();
        this.markForUpdate();
    }

    public int getProgress() {
        return this.progress;
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    public CrystalGrowthRecipe getCurrentRecipe() {
        return this.currentRecipe;
    }

    public ItemStack getClientDisplayStack() {
        return this.clientDisplayStack;
    }

    public FluidStack getInputFluid() {
        return this.inputTank.getFluid();
    }

    public void setInputFluid(@Nullable FluidStack fluid) {
        if (fluid != null && fluid.amount > this.inputTank.getCapacity()) {
            fluid = new FluidStack(fluid, this.inputTank.getCapacity());
        }
        this.inputTank.setFluid(fluid);
        this.saveChanges();
        this.markForUpdate();
    }

    public FluidStack getOutputFluid() {
        return this.outputTank.getFluid();
    }

    public void setOutputFluid(@Nullable FluidStack fluid) {
        if (fluid != null && fluid.amount > this.outputTank.getCapacity()) {
            fluid = new FluidStack(fluid, this.outputTank.getCapacity());
        }
        this.outputTank.setFluid(fluid);
        this.saveChanges();
        this.markForUpdate();
    }

    public FluidTank getInputTank() {
        return this.inputTank;
    }

    public FluidTank getOutputTank() {
        return this.outputTank;
    }

    public int getMaxFluidMB() {
        return 5000;
    }

    // -------------------------------------------------------------- face eject GUI

    public EnumFacing guiIndexToEnumFacing(int guiIndex) {
        switch (guiIndex) {
            case 0: return EnumFacing.UP;
            case 1: return this.getForward().rotateY();
            case 2: return this.getForward();
            case 3: return this.getForward().rotateYCCW();
            case 4: return EnumFacing.DOWN;
            case 5: return this.getForward().getOpposite();
            default: return null;
        }
    }

    public int enumFacingToGuiIndex(EnumFacing facing) {
        if (facing == EnumFacing.UP) return 0;
        if (facing == this.getForward().rotateY()) return 1;
        if (facing == this.getForward()) return 2;
        if (facing == this.getForward().rotateYCCW()) return 3;
        if (facing == EnumFacing.DOWN) return 4;
        if (facing == this.getForward().getOpposite()) return 5;
        return -1;
    }

    public boolean isFaceEjecting(int faceIndex) {
        EnumFacing facing = this.guiIndexToEnumFacing(faceIndex);
        return facing != null && this.faceEjectConfig[facing.getIndex()];
    }

    public void setFaceEjecting(int faceIndex, boolean value) {
        EnumFacing facing = this.guiIndexToEnumFacing(faceIndex);
        if (facing != null) {
            this.faceEjectConfig[facing.getIndex()] = value;
            this.saveChanges();
            this.markForUpdate();
        }
    }

    // ------------------------------------------------------------------- upgrades

    public int getSpeedCardCount() {
        int count = 0;
        for (int i = 0; i < this.upgradeInv.size(); i++) {
            if (AEItems.SPEED_CARD.isSameAs(this.upgradeInv.getStackInSlot(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Installed AE2S parallel cards, capped at five.
     *
     * <p>Each card doubles the batch size, so five cards allow up to 32 runs at once.</p>
     */
    public int getParallelCardCount() {
        int count = 0;
        for (int i = 0; i < this.upgradeInv.size(); i++) {
            if (AEItems.PARALLEL_CARD.isSameAs(this.upgradeInv.getStackInSlot(i))) {
                count++;
            }
        }
        return Math.min(MAX_PARALLEL_CARDS, count);
    }

    /** Largest batch the installed parallel cards allow. */
    public int getMaxParallelRuns() {
        return 1 << this.getParallelCardCount();
    }

    public int getAdjustedProcessingTime(int baseTime) {
        int cards = this.getSpeedCardCount();
        if (cards <= 0) {
            return baseTime;
        }
        double adjusted = baseTime * Math.pow(SPEED_CARD_MULTIPLIER, cards);
        return Math.max(1, (int) Math.round(adjusted));
    }

    public double getAdjustedEnergyPerTick(double totalEnergy, int baseTime) {
        int adjustedTime = this.getAdjustedProcessingTime(baseTime);
        return adjustedTime > 0 ? totalEnergy / adjustedTime : 0;
    }

    // ------------------------------------------------------------------- ticking

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 40, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        CrystalGrowthRecipes.init();

        if (this.getInternalCurrentPower() < this.getInternalMaxPower()) {
            double demand = Math.min(EXTRACT_RATE, this.getInternalMaxPower() - this.getInternalCurrentPower());
            IEnergyService energy = node.grid().getEnergyService();
            double extracted = energy.extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.ONE);
            if (extracted > 0) {
                this.injectAEPower(extracted, Actionable.MODULATE);
            }
        }

        if (this.ejecting) {
            this.ejectItems();
        }

        if (this.currentRecipe != null) {
            return this.processRecipe(ticksSinceLastCall);
        }
        TickRateModulation result = this.tryStartRecipe();
        if (com.ae2utilix.ae2_utilix.config.AE2UtilixConfig.debugCrystalGrowth) {
            com.ae2utilix.ae2_utilix.AE2Utilix.LOGGER.info(
                    "CGC tick at {}: powered={} power={}/{} recipe={} inputs={} fluid={}",
                    this.pos, this.powered, this.getInternalCurrentPower(), this.getInternalMaxPower(),
                    this.currentRecipe != null, describeInputs(), this.inputTank.getFluid());
        }
        return result;
    }

    /** Summarises the nine input slots for the diagnostic log. */
    private String describeInputs() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = this.inputInv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(stack.getCount()).append('x').append(stack.getDisplayName());
            }
        }
        return out.length() == 0 ? "-" : out.toString();
    }


    private void ejectItems() {
        for (EnumFacing facing : EnumFacing.values()) {
            if (!this.faceEjectConfig[facing.getIndex()]) {
                continue;
            }
            net.minecraft.tileentity.TileEntity te = this.world.getTileEntity(this.pos.offset(facing));
            if (te == null) {
                continue;
            }

            IItemHandler target = te.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                    facing.getOpposite());
            if (target != null) {
                for (int i = 0; i < this.outputInv.size(); i++) {
                    ItemStack stack = this.outputInv.getStackInSlot(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(target, stack, true);
                    int toExtract = stack.getCount() - remaining.getCount();
                    if (toExtract <= 0) {
                        continue;
                    }
                    ItemStack extracted = this.outputInv.extractItem(i, toExtract, false);
                    if (!extracted.isEmpty()) {
                        ItemHandlerHelper.insertItemStacked(target, extracted, false);
                    }
                }
            }

            IFluidHandler targetTank = te.getCapability(
                    net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                    facing.getOpposite());
            FluidStack output = this.outputTank.getFluid();
            if (targetTank != null && output != null && output.amount > 0) {
                FluidStack toDrain = output.copy();
                int filled = targetTank.fill(toDrain, true);
                if (filled > 0) {
                    this.outputTank.drain(filled, true);
                }
            }
        }
    }

    private TickRateModulation tryStartRecipe() {
        int maxParallelRuns = this.getMaxParallelRuns();
        CrystalGrowthRecipe recipe = null;
        int parallelRuns = 0;
        // Parallel cards give a maximum batch size, not a required one, so fall
        // back to the largest batch the current inputs and output space allow.
        for (int candidateRuns = maxParallelRuns; candidateRuns >= 1; candidateRuns--) {
            CrystalGrowthRecipe candidate = CrystalGrowthRecipes.findMatchingRecipe(
                    this.inputInv.toItemHandler(), this.inputTank.getFluid(), candidateRuns);
            if (candidate == null
                    || !candidate.canFitOutputs(this.outputInv.toItemHandler(), candidateRuns)
                    || !candidate.canFitFluidOutput(this.outputTank, candidateRuns)) {
                continue;
            }
            recipe = candidate;
            parallelRuns = candidateRuns;
            break;
        }

        if (recipe == null) {
            if (this.progress > 0) {
                this.progress = 0;
                this.maxProgress = 100;
                this.markForUpdate();
            }
            return TickRateModulation.SLOWER;
        }

        double energyPerTick = this.getAdjustedEnergyPerTick(recipe.getEnergyCost(), recipe.getProcessingTime())
                * parallelRuns;
        if (this.getInternalCurrentPower() < energyPerTick) {
            return TickRateModulation.SLOWER;
        }

        this.currentRecipe = recipe;
        this.currentParallelRuns = parallelRuns;
        this.maxProgress = this.getAdjustedProcessingTime(recipe.getProcessingTime());
        this.progress = 0;

        return TickRateModulation.FASTER;
    }

    private TickRateModulation processRecipe(int ticksSinceLastCall) {
        int parallelRuns = Math.max(1, this.currentParallelRuns);
        double energyPerTick = this.getAdjustedEnergyPerTick(this.currentRecipe.getEnergyCost(),
                this.currentRecipe.getProcessingTime()) * parallelRuns;

        if (this.getInternalCurrentPower() < energyPerTick) {
            return TickRateModulation.SLOWER;
        }

        this.extractAEPower(energyPerTick, Actionable.MODULATE);
        this.progress += ticksSinceLastCall;
        this.markForUpdate();

        if (this.progress >= this.maxProgress) {
            if (this.hasInputsFor(parallelRuns)
                    && this.currentRecipe.canFitOutputs(this.outputInv.toItemHandler(), parallelRuns)
                    && this.currentRecipe.canFitFluidOutput(this.outputTank, parallelRuns)) {
                // Inputs are consumed only once the batch actually completes, so quitting
                // mid-batch cannot silently destroy materials.
                if (this.currentRecipe.getInputFluid() != null) {
                    FluidStack drainFluid = this.currentRecipe.getInputFluid().copy();
                    drainFluid.amount *= parallelRuns;
                    this.inputTank.drain(drainFluid, true);
                }
                for (int i = 0; i < parallelRuns; i++) {
                    this.currentRecipe.consumeInputs(this.inputInv.toItemHandler());
                }
                for (int i = 0; i < parallelRuns; i++) {
                    this.currentRecipe.produceOutputs(this.outputInv.toItemHandler());
                    this.currentRecipe.produceFluidOutput(this.outputTank);
                }
                this.saveChanges();
                this.currentRecipe = null;
                this.currentParallelRuns = 0;
                this.progress = 0;
                this.maxProgress = 100;
                TickRateModulation result = this.tryStartRecipe();
                return result == TickRateModulation.IDLE ? TickRateModulation.URGENT : result;
            }

            // The batch finished its work but the payout is blocked: the inputs were
            // removed or the output slots filled up. Keep the finished batch and retry,
            // so nothing the player already paid for is silently thrown away.
            this.saveChanges();
            return TickRateModulation.SLOWER;
        }

        return TickRateModulation.FASTER;
    }

    /**
     * Confirms the batch is still fully chargeable when it finishes.
     *
     * <p>Inputs are only taken at completion, so the player can empty the tank or pull
     * items out mid-batch. Without this check the machine would produce outputs it never
     * paid for.</p>
     */
    private boolean hasInputsFor(int parallelRuns) {
        if (this.currentRecipe == null) {
            return false;
        }
        return this.currentRecipe.matches(this.inputInv.toItemHandler(),
                this.inputTank.getFluid(), parallelRuns);
    }

    // -------------------------------------------------------------- client sync

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        PacketBuffer buffer = new PacketBuffer(data);
        buffer.writeBoolean(this.ejecting);
        buffer.writeBoolean(this.powered);
        int faceMask = 0;
        for (int i = 0; i < 6; i++) {
            if (this.faceEjectConfig[i]) {
                faceMask |= 1 << i;
            }
        }
        buffer.writeByte(faceMask);
        buffer.writeInt(this.progress);
        buffer.writeInt(this.maxProgress);
        buffer.writeCompoundTag(this.inputTank.writeToNBT(new NBTTagCompound()));
        buffer.writeCompoundTag(this.outputTank.writeToNBT(new NBTTagCompound()));
        ItemStack display = this.clientDisplayStack;
        if (display.isEmpty() && this.currentRecipe != null && !this.currentRecipe.getOutputs().isEmpty()) {
            display = this.currentRecipe.getOutputs().get(0);
        }
        buffer.writeItemStack(display);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);

        boolean oldEjecting = this.ejecting;
        boolean oldPowered = this.powered;
        int oldFaceMask = 0;
        for (int i = 0; i < 6; i++) {
            if (this.faceEjectConfig[i]) {
                oldFaceMask |= 1 << i;
            }
        }
        int oldProgress = this.progress;
        int oldMaxProgress = this.maxProgress;
        FluidStack oldInputFluid = this.inputTank.getFluid();
        FluidStack oldOutputFluid = this.outputTank.getFluid();
        ItemStack oldDisplay = this.clientDisplayStack;

        PacketBuffer buffer = new PacketBuffer(data);
        int faceMask = 0;
        try {
            this.ejecting = buffer.readBoolean();
            this.powered = buffer.readBoolean();
            faceMask = buffer.readUnsignedByte();
            for (int i = 0; i < 6; i++) {
                this.faceEjectConfig[i] = (faceMask & (1 << i)) != 0;
            }
            this.progress = buffer.readInt();
            this.maxProgress = buffer.readInt();
            this.inputTank.readFromNBT(buffer.readCompoundTag());
            this.outputTank.readFromNBT(buffer.readCompoundTag());
            this.clientDisplayStack = buffer.readItemStack();
        } catch (IOException e) {
            return changed;
        }

        // AE2 only refreshes the client block when something actually changed, so every
        // field is compared instead of unconditionally reporting a difference.
        changed |= oldEjecting != this.ejecting;
        changed |= oldPowered != this.powered;
        changed |= oldFaceMask != faceMask;
        changed |= oldProgress != this.progress;
        changed |= oldMaxProgress != this.maxProgress;
        changed |= !ItemStack.areItemStacksEqual(oldDisplay, this.clientDisplayStack);
        changed |= !fluidEquals(oldInputFluid, this.inputTank.getFluid());
        changed |= !fluidEquals(oldOutputFluid, this.outputTank.getFluid());
        return changed;
    }

    private static boolean fluidEquals(@Nullable FluidStack first, @Nullable FluidStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.isFluidEqual(second) && first.amount == second.amount;
    }

    // ------------------------------------------------------------------ caps

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.combinedFluidHandler;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void addAdditionalDrops(java.util.List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.inputTank.setFluid(null);
        this.outputTank.setFluid(null);
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] packBooleans(boolean[] values) {
        byte[] packed = new byte[(values.length + 7) / 8];
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                packed[i / 8] |= (byte) (1 << (i % 8));
            }
        }
        return packed;
    }

    private static void unpackBooleans(byte[] packed, boolean[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = i / 8 < packed.length && ((packed[i / 8] >> (i % 8)) & 1) == 1;
        }
    }

    private static final class CombinedInvWrapper {
        private CombinedInvWrapper() {
        }
    }

    /** Hides the output and upgrade slots from automation. */
    private final class ExternalItemHandler extends ae2.api.inventories.BaseInternalInventory {
        @Override
        public int size() {
            return TileCrystalGrowthChamber.this.combinedInv.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return TileCrystalGrowthChamber.this.combinedInv.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            TileCrystalGrowthChamber.this.combinedInv.setItemDirect(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUT_SLOTS) {
                return stack;
            }
            return TileCrystalGrowthChamber.this.combinedInv.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < INPUT_SLOTS) {
                return ItemStack.EMPTY;
            }
            return TileCrystalGrowthChamber.this.combinedInv.extractItem(slot, amount, simulate);
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
                saveChanges();
                markForUpdate();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            FluidStack drained = super.drain(resource, doDrain);
            if (drained != null && drained.amount > 0 && doDrain) {
                saveChanges();
                markForUpdate();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = super.drain(maxDrain, doDrain);
            if (drained != null && drained.amount > 0 && doDrain) {
                saveChanges();
                markForUpdate();
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