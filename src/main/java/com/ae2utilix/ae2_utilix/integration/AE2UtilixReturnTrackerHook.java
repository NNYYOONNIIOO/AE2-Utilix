package com.ae2utilix.ae2_utilix.integration;

/**
 * Lets the pattern provider mixins expose their shared product return tracker.
 *
 * <p>This interface deliberately lives outside the {@code mixin} package: Mixin forbids
 * directly referencing any class inside a configured mixin package, so a helper that is
 * loaded at runtime (and used from {@code instanceof} checks) has to live elsewhere.</p>
 */
public interface AE2UtilixReturnTrackerHook {

    ProductReturnTracker ae2utilix$tracker();
}