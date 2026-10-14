package com.ae2utilix.ae2_utilix.mixin;

import com.ae2utilix.ae2_utilix.integration.ProductReturnTracker;

/**
 * Implemented by the pattern provider mixins so the shared product return tracker can be
 * reached from the injected code.
 */
public interface AE2UtilixReturnTrackerHook {

    ProductReturnTracker ae2utilix$tracker();
}