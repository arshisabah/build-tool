package com.arshi.api;

/**
 * The default build lifecycle, in execution order.
 * Running "arshi package" executes every phase up to and including PACKAGE.
 */
public enum Phase {
    VALIDATE,
    COMPILE,
    TEST,
    PACKAGE,
    INSTALL,
    DEPLOY
}
