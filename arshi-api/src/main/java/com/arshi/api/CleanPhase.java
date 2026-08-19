package com.arshi.api;

/**
 * The "clean" lifecycle is separate from the default lifecycle,
 * exactly as it is in real Maven. "arshi clean" never triggers compile/test/etc.
 */
public enum CleanPhase {
    CLEAN
}
