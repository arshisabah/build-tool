package com.arshi.api;

/** Binds a goal (e.g. "compiler:compile") to a lifecycle phase. */
public record PluginBinding(String goal, Phase phase) {}
