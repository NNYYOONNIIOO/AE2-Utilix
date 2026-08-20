package com.ae2utilix.gui;

import appeng.api.AEApi;
import appeng.helpers.InventoryAction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.*;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.me.helpers.MachineSource;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.item.AEItemStack;
import com.ae2utilix.block.terminal.TilePatternTerminal;
import com.ae2utilix.AE2Utilix;
import com.ae2utilix.integration.AE2FCRUCompat;
import com.ae2utilix.integration.RandomComplementCompat;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;

public class ContainerFullPattern extends ContainerMEMonitorable
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket {

    private final TilePatternTerminal patternTerminal;

    final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    protected IItemHandler crafting;
    protected CraftingGridView craftingView;
    protected SlotPatternTerm craftSlot;
    protected SlotRestrictedInput patternSlotIN;
    protected SlotRestrictedInput patternSlotOUT;
    protected IRecipe currentRecipe;

    protected SlotFakeCraftingMatrix[] craftingSlots;
    protected OptionalSlotFake[] outputSlots;
    private boolean rc$refillBlankPatterns = true;
    @GuiSync(66)
    public String rc$autoFillPattern = "CLOSE";

    @GuiSync(97)
    public boolean craftingMode = true;
    @GuiSync(96)
    public boolean substitute = false;
    @GuiSync(95)
    public int currentPage = 0;

    // AE2FCRU sync fields
    @GuiSync(105)
    public boolean combine = false;
    @GuiSync(106)
    public boolean fluidFirst = false;

    public ContainerFullPattern(InventoryPlayer ip, ITerminalHost monitorable) {
        super(ip, monitorable, false);
        this.patternTerminal = (TilePatternTerminal) monitorable;
        this.rc$autoFillPattern = RandomComplementCompat.getAutoFillName(this.patternTerminal.getRandomComplementConfigManager());

        this.craftingSlots = new SlotFakeCraftingMatrix[TilePatternTerminal.TOTAL_INPUT_SLOTS];
        this.outputSlots = new OptionalSlotFake[TilePatternTerminal.TOTAL_OUTPUT_SLOTS];

        final IItemHandler patternInv = this.patternTerminal.getInventoryByName("pattern");
        final IItemHandler output = this.patternTerminal.getInventoryByName("output");

        this.crafting = this.patternTerminal.getInventoryByName("crafting");

        // Create a view that limits SlotPatternTerm to only see 9 slots (current page)
        this.craftingView = new CraftingGridView(this.crafting, 0, 9);

        // Create 9 pages of input slots, 9 per page (3x3)
        for (int page = 0; page < TilePatternTerminal.PAGE_COUNT; page++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    int slotIndex = page * 9 + x + y * 3;
                    this.addSlotToContainer(this.craftingSlots[slotIndex] =
                            new SlotFakeCraftingMatrix(this.crafting, slotIndex, 18 + x * 18, -74 + y * 18));
                }
            }
        }

        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), this
                .getPowerSource(), monitorable, this.craftingView, patternInv, this.cOut, 110, -56, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // Create 9 pages of output slots, 3 per page
        for (int page = 0; page < TilePatternTerminal.PAGE_COUNT; page++) {
            for (int y = 0; y < 3; y++) {
                int slotIndex = page * 3 + y;
                this.addSlotToContainer(this.outputSlots[slotIndex] = new SlotPatternOutputs(output, this, slotIndex, 110, -74 + y * 18, 0, 0, 1));
                this.outputSlots[slotIndex].setRenderDisabled(false);
                this.outputSlots[slotIndex].setIIcon(-1);
            }
        }

        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN, patternInv, 0, 147, -79, this
                        .getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, patternInv, 1, 147, -36, this
                        .getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);

        this.bindPlayerInventory(ip, 0, 0);
        this.updateOrderOfOutputSlots();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if (idx == 1) {
            // Processing mode output slots
            return Platform.isServer() ? !this.patternTerminal.isCraftingRecipe() : !this.isCraftingMode();
        } else if (idx == 2) {
            // Crafting mode output slot
            return Platform.isServer() ? this.patternTerminal.isCraftingRecipe() : this.isCraftingMode();
        }
        return false;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(TilePatternTerminal.PAGE_COUNT - 1, page));
        this.craftingView.setOffset(this.currentPage * 9);
        this.updateOrderOfOutputSlots();
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }
        if (this.inventorySlots.get(idx) instanceof SlotPlayerInv || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar) {
            final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx);
            ItemStack itemStack = clickSlot.getStack();
            if (AEApi.instance().definitions().materials().blankPattern().isSameAs(itemStack)) {
                IItemHandler patternInv = this.patternTerminal.getInventoryByName("pattern");
                ItemStack remainder = patternInv.insertItem(0, itemStack, false);
                clickSlot.putStack(remainder);
            }
        }
        return super.transferStackInSlot(p, idx);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if (name.equals("player")) {
            return new PlayerInvWrapper(this.getInventoryPlayer());
        }
        return this.patternTerminal.getInventoryByName(name);
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        if (inv == this.crafting) {
            this.fixCraftingRecipes();
        }
    }

    void fixCraftingRecipes() {
        if (this.isCraftingMode()) {
            for (int x = 0; x < this.crafting.getSlots(); x++) {
                final ItemStack is = this.crafting.getStackInSlot(x);
                if (!is.isEmpty()) {
                    is.setCount(1);
                }
            }
        }
    }

    @Override
    public void putStackInSlot(int slotID, ItemStack stack) {
        super.putStackInSlot(slotID, stack);
        this.getAndUpdateOutput();
    }

    protected void updateOrderOfOutputSlots() {
        if (!this.isCraftingMode()) {
            // Processing mode
            if (craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
            for (int page = 0; page < TilePatternTerminal.PAGE_COUNT; page++) {
                boolean isCurrentPage = page == this.currentPage;
                // Input slots
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        int slotIndex = page * 9 + x + y * 3;
                        if (isCurrentPage) {
                            this.craftingSlots[slotIndex].xPos = 18 + x * 18;
                        } else {
                            this.craftingSlots[slotIndex].xPos = -9000;
                        }
                    }
                }
                // Output slots
                for (int y = 0; y < 3; y++) {
                    int slotIndex = page * 3 + y;
                    if (isCurrentPage) {
                        this.outputSlots[slotIndex].xPos = 110;
                    } else {
                        this.outputSlots[slotIndex].xPos = -9000;
                    }
                }
            }
        } else {
            // Crafting mode: only show first page input slots and crafting output slot
            if (craftSlot != null) {
                this.craftSlot.xPos = 110;
            }
            for (int page = 0; page < TilePatternTerminal.PAGE_COUNT; page++) {
                boolean isFirstPage = page == 0;
                // Input slots
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        int slotIndex = page * 9 + x + y * 3;
                        if (isFirstPage) {
                            this.craftingSlots[slotIndex].xPos = 18 + x * 18;
                        } else {
                            this.craftingSlots[slotIndex].xPos = -9000;
                        }
                    }
                }
                // Output slots all hidden (crafting mode uses craftSlot)
                for (int y = 0; y < 3; y++) {
                    int slotIndex = page * 3 + y;
                    this.outputSlots[slotIndex].xPos = -9000;
                }
            }
        }
    }

    @Override
    public void onSlotChange(final Slot s) {
        if (s == this.patternSlotOUT && Platform.isServer()) {
            for (final IContainerListener listener : this.listeners) {
                for (final Slot slot : this.inventorySlots) {
                    if (slot instanceof OptionalSlotFake || slot instanceof SlotFakeCraftingMatrix) {
                        listener.sendSlotContents(this, slot.slotNumber, slot.getStack());
                    }
                }
                if (listener instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) listener).isChangingQuantityOnly = false;
                }
            }
            this.detectAndSendChanges();
        }
        if (s == this.craftSlot && Platform.isClient()) {
            this.getAndUpdateOutput();
        }
    }

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slotId, final long id) {
        // AE2FCRU fluid interception: in processing mode, LEFT-CLICK converts fluid containers to fluid fake items
        // Left-click = mark fluid (e.g. water), Right-click = mark item (e.g. water bucket)
        if (!this.isCraftingMode() && action == InventoryAction.PICKUP_OR_SET_DOWN) {
            if (AE2FCRUCompat.isLoaded()) {
                try {
                    if (id == 0 && slotId >= 0 && slotId < this.inventorySlots.size()) {
                        final Slot slot = getSlot(slotId);
                        final ItemStack stack = player.inventory.getItemStack();
                        if ((slot instanceof SlotFakeCraftingMatrix || slot instanceof SlotPatternOutputs) && !stack.isEmpty()
                                && stack.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
                                && AE2FCRUCompat.getFluidFromItem(stack) != null) {
                            net.minecraftforge.fluids.FluidStack fluid = AE2FCRUCompat.getFluidFromItem(stack);
                            ItemStack packed = AE2FCRUCompat.packFluid2Drops(fluid);
                            if (packed != null) {
                                slot.putStack(packed);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    AE2Utilix.LOGGER.error("doAction: Unexpected exception in fluid interception", e);
                }
            }
        }
        super.doAction(player, action, slotId, id);
    }

    public void encodeAndMoveToInventory() {
        rc$refillBlankPatternsDirect();
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (!output.isEmpty()) {
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.dropItem(output, false);
            }
            this.patternSlotOUT.putStack(ItemStack.EMPTY);
        }
    }

    public void encode() {
        rc$refillBlankPatternsDirect();
        ItemStack output = this.patternSlotOUT.getStack();

        // AE2FCRU: Check for fluid pattern FIRST (before getInputs/getOutputs)
        // This matches ContainerFluidPatternTerminal.encode() flow
        if (AE2FCRUCompat.isLoaded()) {
            if (!this.craftingMode && this.checkHasFluidPattern()) {
                // Fluid encoding path (DENSE_ENCODED_PATTERN)
                if (output.isEmpty()) {
                    output = this.patternSlotIN.getStack();
                    if (output.isEmpty() || !isPattern(output)) {
                        return;
                    }
                    if (output.getCount() == 1) {
                        this.patternSlotIN.putStack(ItemStack.EMPTY);
                    } else {
                        output.shrink(1);
                    }
                    this.encodeFluidPattern();
                } else if (isPattern(output)) {
                    this.encodeFluidPattern();
                }
                return;
            }
            // No fluid pattern but output has a non-AE2 pattern: convert back to blank
            if (!output.isEmpty()) {
                boolean isStandardPattern = AEApi.instance().definitions().items().encodedPattern().isSameAs(output)
                        || AEApi.instance().definitions().materials().blankPattern().isSameAs(output);
                if (!isStandardPattern) {
                    if (this.patternSlotIN.getStack().isEmpty()) {
                        this.patternSlotIN.putStack(AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orElse(ItemStack.EMPTY));
                    } else {
                        this.patternSlotIN.getStack().grow(1);
                    }
                    this.patternSlotOUT.putStack(ItemStack.EMPTY);
                }
            }
        }

        // Standard encoding (same as ContainerPatternEncoder.encode())
        output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();

        if (in == null || out == null) {
            return;
        }

        if (!output.isEmpty() && !this.isPattern(output)) {
            return;
        } else if (output.isEmpty()) {
            output = this.patternSlotIN.getStack();
            if (output.isEmpty() || !this.isPattern(output)) {
                return;
            }

            output.setCount(output.getCount() - 1);
            if (output.getCount() == 0) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }

            Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
            if (maybePattern.isPresent()) {
                output = maybePattern.get();
            }
        }

        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final ItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }

        for (final ItemStack i : out) {
            tagOut.appendTag(this.createItemTag(i));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());

        output.setTagCompound(encodedValue);

        patternSlotOUT.putStack(output);
    }

    public void multiply(int multiple) {
        boolean canMultiplyInputs = true;
        boolean canMultiplyOutputs = true;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() * multiple < 1) {
                canMultiplyInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() * multiple < 1) {
                canMultiplyOutputs = false;
            }
        }
        if (canMultiplyInputs && canMultiplyOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() * multiple);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() * multiple);
                }
            }
        }
    }

    public void divide(int divide) {
        boolean canDivideInputs = true;
        boolean canDivideOutputs = true;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() % divide != 0) {
                canDivideInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() % divide != 0) {
                canDivideOutputs = false;
            }
        }
        if (canDivideInputs && canDivideOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() / divide);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() / divide);
                }
            }
        }
    }

    public void increase(int increase) {
        boolean canIncreaseInputs = true;
        boolean canIncreaseOutputs = true;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() + increase < 1) {
                canIncreaseInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() + increase < 1) {
                canIncreaseOutputs = false;
            }
        }
        if (canIncreaseInputs && canIncreaseOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() + increase);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() + increase);
                }
            }
        }
    }

    public void decrease(int decrease) {
        boolean canDecreaseInputs = true;
        boolean canDecreaseOutputs = true;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() - decrease < 1) {
                canDecreaseInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() - decrease < 1) {
                canDecreaseOutputs = false;
            }
        }
        if (canDecreaseInputs && canDecreaseOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() - decrease);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() - decrease);
                }
            }
        }
    }

    protected ItemStack[] getInputs() {
        if (this.isCraftingMode()) {
            // Crafting mode: only use page 0's 9 inputs
            final ItemStack[] input = new ItemStack[9];
            boolean hasValue = false;
            for (int x = 0; x < 9; x++) {
                input[x] = this.craftingSlots[x].getStack();
                if (!input[x].isEmpty()) {
                    hasValue = true;
                }
            }
            if (hasValue) {
                return input;
            }
            return null;
        } else {
            // Processing mode: return all non-empty inputs across all pages
            final List<ItemStack> list = new ArrayList<>();
            boolean hasValue = false;
            for (int i = 0; i < TilePatternTerminal.TOTAL_INPUT_SLOTS; i++) {
                final ItemStack stack = this.craftingSlots[i].getStack();
                if (!stack.isEmpty()) {
                    list.add(stack);
                    hasValue = true;
                } else {
                    list.add(ItemStack.EMPTY);
                }
            }
            if (hasValue) {
                return list.toArray(new ItemStack[0]);
            }
            return null;
        }
    }

    protected ItemStack[] getOutputs() {
        if (this.isCraftingMode()) {
            final ItemStack out = this.getAndUpdateOutput();
            if (!out.isEmpty() && out.getCount() > 0) {
                return new ItemStack[]{out};
            }
        } else {
            // Processing mode: return all non-empty outputs across all pages
            final List<ItemStack> list = new ArrayList<>();
            boolean hasValue = false;
            for (int i = 0; i < TilePatternTerminal.TOTAL_OUTPUT_SLOTS; i++) {
                final ItemStack out = this.outputSlots[i].getStack();
                if (!out.isEmpty() && out.getCount() > 0) {
                    list.add(out);
                    hasValue = true;
                } else {
                    list.add(ItemStack.EMPTY);
                }
            }
            if (hasValue) {
                return list.toArray(new ItemStack[0]);
            }
        }
        return null;
    }

    protected ItemStack getAndUpdateOutput() {
        final World world = this.getPlayerInv().player.world;
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        // Crafting mode always uses page 0; processing mode uses current page
        int pageOffset = this.isCraftingMode() ? 0 : this.currentPage * 9;
        for (int x = 0; x < 9; x++) {
            ic.setInventorySlotContents(x, this.crafting.getStackInSlot(pageOffset + x));
        }

        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, world);
        }

        final ItemStack is;
        if (this.currentRecipe == null) {
            is = ItemStack.EMPTY;
        } else {
            is = this.currentRecipe.getCraftingResult(ic);
        }

        this.cOut.setStackInSlot(0, is);
        return is;
    }

    public boolean isCraftingMode() {
        return this.craftingMode;
    }

    public void setCraftingMode(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        if (this.patternTerminal != null) {
            this.patternTerminal.setCraftingRecipe(craftingMode);
        }
        if (craftingMode) {
            this.fixCraftingRecipes();
            // Crafting mode only uses page 0
            this.currentPage = 0;
            this.craftingView.setOffset(0);
        }
        this.getAndUpdateOutput();
        this.updateOrderOfOutputSlots();
    }

    boolean isSubstitute() {
        return this.substitute;
    }

    public void setSubstitute(final boolean substitute) {
        this.substitute = substitute;
        if (this.patternTerminal != null) {
            this.patternTerminal.setSubstitute(substitute);
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            if (this.patternTerminal != null) {
                if (this.isCraftingMode() != this.patternTerminal.isCraftingRecipe()) {
                    this.setCraftingMode(this.patternTerminal.isCraftingRecipe());
                    this.updateOrderOfOutputSlots();
                }
                this.substitute = this.patternTerminal.isSubstitute();
            }
            // Sync AE2FCRU fields
            this.combine = this.patternTerminal.getCombineMode();
            this.fluidFirst = this.patternTerminal.getFluidPlaceMode();
            this.rc$autoFillPattern = RandomComplementCompat.getAutoFillName(this.patternTerminal.getRandomComplementConfigManager());
            if (this.rc$refillBlankPatterns) {
                this.rc$refillBlankPatternsDirect();
                this.rc$refillBlankPatterns = false;
            }
        }
    }

    public String r$getAutoFillPatternName() {
        return this.rc$autoFillPattern;
    }

    private void rc$refillBlankPatternsDirect() {
        if (!"OPEN".equals(this.rc$autoFillPattern) || !Platform.isServer()) return;
        ItemStack blanks = this.patternSlotIN.getStack();
        int blanksToRefill = 64 - blanks.getCount();
        if (blanksToRefill <= 0) return;
        Optional<ItemStack> blankPattern = AEApi.instance().definitions().materials().blankPattern().maybeStack(blanksToRefill);
        if (!blankPattern.isPresent()) return;
        IAEItemStack request = AEItemStack.fromItemStack(blankPattern.get());
        IAEItemStack extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), request, this.getActionSource());
        if (extracted == null) return;
        if (blanks.isEmpty()) {
            blanks = request.getDefinition().copy();
            blanks.setCount((int) extracted.getStackSize());
        } else {
            blanks.setCount((int) (blanks.getCount() + extracted.getStackSize()));
        }
        this.patternSlotIN.putStack(blanks);
    }

    // FCFluidPatternContainer equivalent methods (not interface implementations)
    public boolean getCombineMode() {
        return this.patternTerminal.getCombineMode();
    }

    public void setCombineMode(boolean mode) {
        this.patternTerminal.setCombineMode(mode);
    }

    public boolean getFluidPlaceMode() {
        return this.patternTerminal.getFluidPlaceMode();
    }

    public void setFluidPlaceMode(boolean mode) {
        this.patternTerminal.setFluidPlaceMode(mode);
    }

    public void acceptPattern(Int2ObjectMap<ItemStack[]> inputs, List<ItemStack> outputs, boolean compress) {
        if (this.patternTerminal != null) {
            this.patternTerminal.onChangeCrafting(inputs, outputs, compress);
        }
    }

    public void encodeFluidCraftPattern() {
        Item denseItem = AE2FCRUCompat.getDenseCraftEncodedPattern();
        if (denseItem == null) {
            encode();
            return;
        }

        ItemStack output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();
        if (in == null || out == null) {
            return;
        }

        if (!output.isEmpty() && !isPattern(output)) {
            return;
        } else if (output.isEmpty()) {
            output = this.patternSlotIN.getStack();
            if (output.isEmpty() || !isPattern(output)) {
                return;
            }
            output.setCount(output.getCount() - 1);
            if (output.getCount() == 0) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }
        }

        final NBTTagCompound encodedValue = new NBTTagCompound();
        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final ItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }
        for (final ItemStack i : out) {
            tagOut.appendTag(this.createItemTag(i));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.substitute);

        // Create DENSE_CRAFT_ENCODED_PATTERN via compat layer
        final ItemStack patternStack = new ItemStack(denseItem);
        patternStack.setTagCompound(encodedValue);

        // Validate: if not necessary (no fluid inputs), fall back to standard encode
        final Object details = AE2FCRUCompat.getFluidCraftingPatternDetails(patternStack, this.patternTerminal.getWorld());
        if (details == null || !AE2FCRUCompat.isFluidPatternNecessary(details)) {
            encode();
            return;
        }
        patternSlotOUT.putStack(patternStack);
    }

    /**
     * AE2FCRU: Check if the crafting/output slots contain fluid fake items.
     * Only relevant in processing mode.
     */
    private boolean checkHasFluidPattern() {
        if (!AE2FCRUCompat.isLoaded() || this.craftingMode) {
            return false;
        }

        for (final Slot craftingSlot : this.craftingSlots) {
            final ItemStack crafting = craftingSlot.getStack();
            if (!crafting.isEmpty() && AE2FCRUCompat.isFluidFakeItem(crafting)) {
                return true;
            }
        }

        for (final Slot outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && AE2FCRUCompat.isFluidFakeItem(out)) {
                return true;
            }
        }

        return false;
    }

    /**
     * AE2FCRU: Encode a fluid processing pattern (DENSE_ENCODED_PATTERN).
     * Called by encode() when in processing mode with fluid items.
     */
    private void encodeFluidPattern() {
        Item denseItem = AE2FCRUCompat.getDenseEncodedPattern();
        if (denseItem == null) return;
        final ItemStack patternStack = new ItemStack(denseItem);
        ItemStack result = AE2FCRUCompat.encodeFluidPattern(
                patternStack,
                collectInventory(this.craftingSlots),
                collectInventory(this.outputSlots),
                this.getInventoryPlayer().player.getGameProfile().getId()
        );
        if (result != null) {
            patternSlotOUT.putStack(result);
        }
    }

    private static IAEItemStack[] collectInventory(final Slot[] slots) {
        final java.util.List<IAEItemStack> acc = new java.util.ArrayList<>();
        for (final Slot s : slots) {
            final ItemStack is = s.getStack();
            if (!is.isEmpty()) {
                acc.add(AEItemStack.fromItemStack(is));
            }
        }
        return acc.toArray(new IAEItemStack[0]);
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if (field.equals("craftingMode")) {
            this.getAndUpdateOutput();
            this.updateOrderOfOutputSlots();
        } else if (field.equals("currentPage")) {
            this.craftingView.setOffset(this.currentPage * 9);
            this.updateOrderOfOutputSlots();
        }
    }

    boolean isPattern(final ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        // Check standard AE2 patterns
        boolean isPattern = AEApi.instance().definitions().items().encodedPattern().isSameAs(output);
        isPattern |= AEApi.instance().definitions().materials().blankPattern().isSameAs(output);
        // Check AE2FCRU fluid pattern types via compat layer
        isPattern |= AE2FCRUCompat.isFluidEncodedPattern(output);
        return isPattern;
    }

    NBTBase createItemTag(final ItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();
        if (!i.isEmpty()) {
            stackWriteToNBT(i, c);
        }
        return c;
    }

    public void clear() {
        // Clear all pages
        for (int i = 0; i < TilePatternTerminal.TOTAL_INPUT_SLOTS; i++) {
            this.craftingSlots[i].putStack(ItemStack.EMPTY);
        }
        for (int i = 0; i < TilePatternTerminal.TOTAL_OUTPUT_SLOTS; i++) {
            this.outputSlots[i].putStack(ItemStack.EMPTY);
        }
        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    public void setSlotCount(int slotNumber, int newCount) {
        if (slotNumber >= 0 && slotNumber < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotNumber);
            if (slot instanceof SlotFake) {
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty() && newCount >= 1) {
                    ItemStack newStack = stack.copy();
                    newStack.setCount(newCount);
                    slot.putStack(newStack);
                }
            }
        }
        this.detectAndSendChanges();
    }

    public void craftOrGetItem(final PacketPatternSlot packetPatternSlot) {
        if (packetPatternSlot.slotItem != null && this.getCellInventory() != null) {
            final IAEItemStack out = packetPatternSlot.slotItem.copy();
            InventoryAdaptor inv = new AdaptorItemHandler(new WrapperCursorItemHandler(this.getPlayerInv().player.inventory));
            final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(this.getPlayerInv().player);

            if (packetPatternSlot.shift) {
                inv = playerInv;
            }

            if (!inv.simulateAdd(out.createItemStack()).isEmpty()) {
                return;
            }

            final IAEItemStack extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), out, this.getActionSource());
            final EntityPlayer p = this.getPlayerInv().player;

            if (extracted != null) {
                inv.addItems(extracted.createItemStack());
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
                return;
            }

            final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
            final InventoryCrafting real = new InventoryCrafting(new ContainerNull(), 3, 3);

            for (int x = 0; x < 9; x++) {
                ic.setInventorySlotContents(x, packetPatternSlot.pattern[x] == null ? ItemStack.EMPTY : packetPatternSlot.pattern[x].createItemStack());
            }

            final IRecipe r = CraftingManager.findMatchingRecipe(ic, p.world);
            if (r == null) {
                return;
            }

            IMEMonitor<IAEItemStack> storage = this.patternTerminal
                    .getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            final IItemList<IAEItemStack> all = storage.getStorageList();
            final ItemStack is = r.getCraftingResult(ic);

            for (int x = 0; x < ic.getSizeInventory(); x++) {
                if (!ic.getStackInSlot(x).isEmpty()) {
                    final ItemStack pulled = Platform.extractItemsByRecipe(this.getPowerSource(), this.getActionSource(), storage, p.world, r, is, ic,
                            ic.getStackInSlot(x), x, all, Actionable.MODULATE, ItemViewCell.createFilter(this.getViewCells()));
                    real.setInventorySlotContents(x, pulled);
                }
            }

            final IRecipe rr = CraftingManager.findMatchingRecipe(real, p.world);

            if (rr == r && Platform.itemComparisons().isSameItem(rr.getCraftingResult(real), is)) {
                final InventoryCraftResult craftingResult = new InventoryCraftResult();
                craftingResult.setRecipeUsed(rr);

                final SlotCrafting sc = new SlotCrafting(p, real, craftingResult, 0, 0, 0);
                sc.onTake(p, is);

                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = playerInv.addItems(real.getStackInSlot(x));
                    if (!failed.isEmpty()) {
                        p.dropItem(failed, false);
                    }
                }

                inv.addItems(is);
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
            } else {
                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = real.getStackInSlot(x);
                    if (!failed.isEmpty()) {
                        this.getCellInventory()
                                .injectItems(AEItemStack.fromItemStack(failed), Actionable.MODULATE,
                                        new MachineSource((IActionHost) this.patternTerminal));
                    }
                }
            }
        }
    }
}
