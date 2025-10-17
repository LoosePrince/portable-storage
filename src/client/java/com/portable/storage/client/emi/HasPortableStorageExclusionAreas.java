package com.portable.storage.client.emi;

import dev.emi.emi.api.widget.Bounds;

import java.util.function.Consumer;

/**
 * Screens that overlay Portable Storage UI can implement this to provide EMI exclusion areas.
 */
public interface HasPortableStorageExclusionAreas {
    void getPortableStorageExclusionAreas(Consumer<Bounds> consumer);
}
