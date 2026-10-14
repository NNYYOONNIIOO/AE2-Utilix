package com.ae2utilix.ae2_utilix.integration;

import ae2.api.networking.IManagedGridNode;

/**
 * Exposes the private state of AE2's ME interface logic to the mod's mixins.
 *
 * <p>AE2's ticking callbacks live on the private {@code InterfaceLogic$Ticker} inner class,
 * which only holds a synthetic reference to the logic instance.</p>
 *
 * <p>It lives outside the {@code mixin} package because Mixin forbids referencing classes in
 * a configured mixin package at runtime.</p>
 */
public interface AE2UtilixInterfaceAccess {

    IManagedGridNode ae2utilix$mainNode();
}