/**
 * Pure-Java core of HtzCosmetics: networking, signature checks, manifest/model/PNG parsing, cache.
 *
 * <p>Rules for everything in this package (enforced by SourcePolicyTest):
 * <ul>
 *   <li>No imports from Minecraft, Mojang, Fabric, NeoForge or Forge. It must work on every loader/version.</li>
 *   <li>Java 17 language level only (Forge 1.20.1 runs on Java 17).</li>
 *   <li>Downloaded content is passive data: never code, never reflection, never deserialization of classes.</li>
 * </ul>
 */
package com.heytozzz.htzcosmetics.core;
