package com.portable.storage.client.emi;

import java.util.function.Consumer;

import dev.emi.emi.api.widget.Bounds;

/**
 * Screens that overlay Portable Storage UI can implement this to provide EMI exclusion areas.
 */
public interface HasPortableStorageExclusionAreas {
    void getPortableStorageExclusionAreas(Consumer<Bounds> consumer);
}
