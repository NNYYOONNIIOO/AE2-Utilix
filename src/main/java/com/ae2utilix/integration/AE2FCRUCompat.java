package com.ae2utilix.integration;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.mojang.authlib.GameProfile;
import com.ae2utilix.block.terminal.TilePatternTerminal;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import appeng.tile.inventory.AppEngInternalInventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public class AE2FCRUCompat {

    private static Boolean loaded = null;

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = Loader.isModLoaded("ae2fc");
        }
        return loaded;
    }

    /**
     * Gets the block host stored by AE2FC's native fluid-pattern container.
     * AE2FC names this private field {@code part}; for a normal AE2FC panel it
     * contains the panel part, while for this block terminal it contains the
     * TilePatternTerminal added through the optional Mixin.
     */
    @Nullable
    public static TilePatternTerminal getUtilixPatternTerminal(Object container) {
        if (container == null) return null;
        Class<?> type = container.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("part");
                field.setAccessible(true);
                Object value = field.get(container);
                return value instanceof TilePatternTerminal ? (TilePatternTerminal) value : null;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable e) {
                return null;
            }
        }
        return null;
    }

    public static void sendFluidPatternBtns(String name, String value) {
        if (!isLoaded()) return;
        AE2FCRUOptional.run(() -> AE2FCRUDirectCompat.sendPatternButton(name, value));
    }

    // ---- FakeFluids ----

    public static boolean isFluidFakeItem(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return false;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.common.item.fake.FakeFluids");
            Method m = cls.getMethod("isFluidFakeItem", ItemStack.class);
            return (boolean) m.invoke(null, stack);
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    public static ItemStack packFluid2Drops(FluidStack fluid) {
        if (!isLoaded() || fluid == null) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.common.item.fake.FakeFluids");
            Method m = cls.getMethod("packFluid2Drops", FluidStack.class);
            return (ItemStack) m.invoke(null, fluid);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static FluidStack getFluidFromItem(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.util.Util");
            Method m = cls.getMethod("getFluidFromItem", ItemStack.class);
            return (FluidStack) m.invoke(null, stack);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void clearItemInventory(AppEngInternalInventory inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            inv.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public static int findMax(it.unimi.dsi.fastutil.ints.IntSet keys) {
        int max = 0;
        for (int key : keys) {
            if (key > max) max = key;
        }
        return max;
    }

    public static void fuzzyTransferItems(int index, ItemStack[] src, ItemStack[] dest, IItemList<IAEItemStack> storageList) {
        if (!isLoaded()) return;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.util.Util");
            Method m = cls.getMethod("fuzzyTransferItems", int.class, ItemStack[].class, ItemStack[].class, IItemList.class);
            m.invoke(null, index, src, dest, storageList);
        } catch (Throwable ignored) {
        }
    }

    public static ItemStack[] compress(ItemStack[] items) {
        if (!isLoaded()) return items;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.util.Util");
            Method m = cls.getMethod("compress", ItemStack[].class);
            return (ItemStack[]) m.invoke(null, (Object) items);
        } catch (Throwable e) {
            return items;
        }
    }

    @Nullable
    public static Item getDenseCraftEncodedPattern() {
        if (!isLoaded()) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.loader.FCItems");
            Field f = cls.getField("DENSE_CRAFT_ENCODED_PATTERN");
            return (Item) f.get(null);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static Item getDenseEncodedPattern() {
        if (!isLoaded()) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.loader.FCItems");
            Field f = cls.getField("DENSE_ENCODED_PATTERN");
            return (Item) f.get(null);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean isFluidEncodedPattern(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return false;
        String[] classes = {
                "com.glodblock.github.common.item.ItemFluidEncodedPattern",
                "com.glodblock.github.common.item.ItemFluidCraftEncodedPattern",
                "com.glodblock.github.common.item.ItemLargeEncodedPattern"
        };
        for (String name : classes) {
            try {
                if (Class.forName(name).isInstance(stack.getItem())) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Nullable
    public static Object getFluidCraftingPatternDetails(ItemStack patternStack, World world) {
        if (!isLoaded()) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.util.FluidCraftingPatternDetails");
            return cls.getMethod("GetFluidPattern", ItemStack.class, World.class).invoke(null, patternStack, world);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean isFluidCraftingPatternDetails(Object details) {
        if (!isLoaded() || details == null) return false;
        try {
            return Class.forName("com.glodblock.github.util.FluidCraftingPatternDetails").isInstance(details);
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    public static IAEItemStack[] getOriginInputs(Object details) {
        if (details == null) return null;
        try {
            return (IAEItemStack[]) details.getClass().getMethod("getOriginInputs").invoke(details);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean isFluidPatternNecessary(Object details) {
        if (details == null) return false;
        try {
            return (boolean) details.getClass().getMethod("isNecessary").invoke(details);
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    public static ItemStack encodeFluidPattern(ItemStack patternStack, IAEItemStack[] inputs, IAEItemStack[] outputs, UUID encoder) {
        if (!isLoaded()) return null;
        try {
            Class<?> cls = Class.forName("com.glodblock.github.util.FluidPatternDetails");
            Object pattern = cls.getConstructor(ItemStack.class).newInstance(patternStack);
            cls.getMethod("setInputs", IAEItemStack[].class).invoke(pattern, (Object) inputs);
            cls.getMethod("setOutputs", IAEItemStack[].class).invoke(pattern, (Object) outputs);
            try {
                cls.getMethod("setEncoder", GameProfile.class)
                        .invoke(pattern, new GameProfile(encoder, ""));
            } catch (NoSuchMethodException ignored) {
                // Keep compatibility with older AE2FC builds that accepted a UUID.
                cls.getMethod("setEncoder", UUID.class).invoke(pattern, encoder);
            }
            return (ItemStack) cls.getMethod("writeToStack").invoke(pattern);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static GuiButton createGuiFCImgButton(int x, int y, String buttonType, String actionType) {
        if (!isLoaded()) return null;
        final GuiButton[] result = new GuiButton[1];
        AE2FCRUOptional.run(() -> result[0] = AE2FCRUDirectCompat.createButton(x, y, buttonType, actionType));
        return result[0];
    }

    public static void setButtonHalfSize(GuiButton button, boolean halfSize) {
        if (button == null) return;
        AE2FCRUOptional.run(() -> AE2FCRUDirectCompat.setHalfSize(button, halfSize));
    }

    public static void sendRecipeTransfer(Object container, boolean fluidFirst, boolean combine,
                                          Object recipeLayout, boolean craftMode) {
        if (!isLoaded()) return;
        try {
            Class<?> builderClass = Class.forName("com.glodblock.github.integration.jei.RecipeTransferBuilder");
            Object builder = null;
            for (Constructor<?> constructor : builderClass.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && recipeLayout != null
                        && parameterTypes[0].isAssignableFrom(recipeLayout.getClass())) {
                    builder = constructor.newInstance(recipeLayout);
                    break;
                }
            }
            if (builder == null) return;
            builderClass.getMethod("clearEmptySlot", boolean.class).invoke(builder, !craftMode);
            builderClass.getMethod("putFluidFirst", boolean.class).invoke(builder, fluidFirst);
            Object transfer = builderClass.getMethod("build").invoke(builder);
            Method inputMethod = transfer.getClass().getMethod("getInput");
            Method outputMethod = transfer.getClass().getMethod("getOutput");
            inputMethod.setAccessible(true);
            outputMethod.setAccessible(true);
            Object input = inputMethod.invoke(transfer);
            Object output = outputMethod.invoke(transfer);
            Class<?> packetClass = Class.forName("com.glodblock.github.network.CPacketLoadPattern");
            Object packet = null;
            for (Constructor<?> constructor : packetClass.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 3 && parameterTypes[2] == boolean.class
                        && input != null && output != null
                        && parameterTypes[0].isAssignableFrom(input.getClass())
                        && parameterTypes[1].isAssignableFrom(output.getClass())) {
                    packet = constructor.newInstance(input, output, combine);
                    break;
                }
            }
            if (packet == null) return;
            Class<?> fluidCraftClass = Class.forName("com.glodblock.github.FluidCraft");
            Object proxy = fluidCraftClass.getField("proxy").get(null);
            Object netHandler = proxy.getClass().getField("netHandler").get(proxy);
            for (Method method : netHandler.getClass().getMethods()) {
                if ("sendToServer".equals(method.getName()) && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isAssignableFrom(packet.getClass())) {
                    method.invoke(netHandler, packet);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private interface Action {
        void run();
    }

    private static final class AE2FCRUOptional {
        private static void run(Action action) {
            try {
                action.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
