package com.ae2utilix.ae2_utilix.part;

import ae2.api.parts.IPartItem;
import ae2.api.parts.PartModels;
import ae2.items.parts.PartItem;
import ae2.items.parts.PartModelsHelper;
import com.ae2utilix.ae2_utilix.AE2Utilix;

/**
 * Placement item for the ME block storage bus.
 *
 * <p>This mirrors Supergiant's own {@code PartItem} registration path, so the part models
 * declared on {@link PartBlockStorageBus} are picked up by AE2's model loader.</p>
 */
public class ItemPartBlockStorageBus extends PartItem<PartBlockStorageBus> {

    public ItemPartBlockStorageBus() {
        super(PartBlockStorageBus.class, ItemPartBlockStorageBus::create);
        this.setRegistryName(AE2Utilix.MODID, "block_storage_bus");
        this.setTranslationKey(AE2Utilix.MODID + ".block_storage_bus");
        this.setCreativeTab(AE2Utilix.AE2_UTILIX_TAB);
        PartModels.registerModels(PartModelsHelper.createModels(PartBlockStorageBus.class));
    }

    private static PartBlockStorageBus create(IPartItem<PartBlockStorageBus> partItem) {
        return new PartBlockStorageBus(partItem);
    }
}