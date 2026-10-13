package com.ae2utilix.ae2_utilix.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy implements IProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        com.ae2utilix.ae2_utilix.registry.ModRegistry.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        com.ae2utilix.ae2_utilix.registry.ModRegistry.registerRecipes();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }
}