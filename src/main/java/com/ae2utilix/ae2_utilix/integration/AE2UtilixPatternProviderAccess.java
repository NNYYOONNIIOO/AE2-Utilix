package com.ae2utilix.ae2_utilix.integration;

import ae2.api.networking.IManagedGridNode;
import ae2.helpers.patternprovider.PatternProviderLogicHost;

/**
 * Exposes the private state of AE2's pattern provider logic to the mod's mixins.
 *
 * <p>AE2's ticking callbacks live on the private {@code PatternProviderLogic$Ticker} inner
 * class, which only holds a synthetic reference to the logic instance. This interface lets
 * the ticker mixin reach the provider's node, host and upgrade tracker.</p>
 *
 * <p>It deliberately lives outside the {@code mixin} package: Mixin forbids referencing any
 * class inside a configured mixin package at runtime.</p>
 */
public interface AE2UtilixPatternProviderAccess {

    IManagedGridNode ae2utilix$mainNode();

    PatternProviderLogicHost ae2utilix$host();

    ProductReturnTracker ae2utilix$tracker();
}