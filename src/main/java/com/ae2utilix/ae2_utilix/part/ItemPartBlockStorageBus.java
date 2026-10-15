package com.ae2utilix.ae2_utilix.part;

import ae2.api.parts.IPartItem;
import ae2.api.parts.PartModels;
import ae2.items.parts.PartItem;
import ae2.items.parts.PartModelsHelper;

/**
 * Placement item for the ME block storage bus.
 *
 * <p>This mirrors Supergiant's own {@code PartItem} registration path, so the part models
 * declared on {@link PartBlockStorageBus} are picked up by AE2's model loader.</p>
 */
public class ItemPartBlockStorageBus extends PartItem<PartBlockStorageBus> {

    public ItemPartBlockStorageBus() {
        super(PartBlockStorageBus.class, ItemPartBlockStorageBus::create);
        PartModels.registerModels(PartModelsHelper.createModels(PartBlockStorageBus.class));
    }

    private static PartBlockStorageBus create(IPartItem<PartBlockStorageBus> partItem) {
        return new PartBlockStorageBus(partItem);
    }
}