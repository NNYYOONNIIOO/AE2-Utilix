package com.ae2utilix.ae2_utilix.proxy;

import com.ae2utilix.ae2_utilix.AE2Utilix;
import com.ae2utilix.ae2_utilix.block.TileCrystalGrowthChamber;
import com.ae2utilix.ae2_utilix.gui.ContainerCrystalGrowthChamber;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;

public class CommonProxy implements IProxy, IGuiHandler {

    /** GUI id of the crystal growth chamber. */
    public static final int GUI_CRYSTAL_GROWTH_CHAMBER = 0;
    /** GUI id of the block form terminals, which share one handler. */
    public static final int GUI_FULL_TERMINAL = 1;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        com.ae2utilix.ae2_utilix.registry.ModRegistry.register();
        com.ae2utilix.ae2_utilix.network.NetworkHandler.register();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // AE2 finishes its own upgrade tables during post-init, so card associations are
        // registered afterwards to make sure both sides are present.
        com.ae2utilix.ae2_utilix.registry.ModUpgrades.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        com.ae2utilix.ae2_utilix.registry.ModRegistry.registerRecipes();
        NetworkRegistry.INSTANCE.registerGuiHandler(AE2Utilix.instance(), this);
    }



    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (id == GUI_CRYSTAL_GROWTH_CHAMBER) {
            if (te instanceof TileCrystalGrowthChamber) {
                return new ContainerCrystalGrowthChamber(player.inventory, (TileCrystalGrowthChamber) te);
            }
            return null;
        }
        if (id == GUI_FULL_TERMINAL) {
            return com.ae2utilix.ae2_utilix.gui.FullTerminalGuiHandler.createServerContainer(
                    player, (com.ae2utilix.ae2_utilix.block.terminal.TileFullTerminal) te);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // Client side only; ClientProxy overrides this method.
        return null;
    }
}