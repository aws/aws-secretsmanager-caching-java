/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.amazonaws.secretsmanager.caching.cache;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * An LRU cache based on the Java LinkedHashMap.
 *
 */
public class LRUCache<K, V> {

    private static class LRULinkedHashMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 16L;
        private final int maxSize;

        LRULinkedHashMap(int maxSize) {
            super(16, .75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return (this.size() > this.maxSize);
        }
    }

    /** The hash map used to hold the cached items. */
    private final LRULinkedHashMap<K, V> map;

    /** The default max size for the cache */
    private static final int DEFAULT_MAX_SIZE = 1024;

    /**
     * Construct a new cache with default settings.
     */
    public LRUCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Construct a new cache based on the given max size.
     *
     * @param maxSize
     *            The maximum number of items to store in the cache.
     */
    public LRUCache(int maxSize) {
        this.map = new LRULinkedHashMap<K, V>(maxSize);
    }

    /**
     * Return the value mapped to the given key.
     *
     * @param key
     *            The key used to return the mapped value.
     * @return The value mapped to the given key.
     */
    public V get(final K key) {
        synchronized (map) {
            return map.get(key);
        }
    }

    /**
     * Determine if the cache contains the given key.
     *
     * @param key
     *            The key used to determine it there is a mapped value.
     * @return True if a value is mapped to the given key.
     */
    public boolean containsKey(final K key) {
        synchronized (map) {
            return map.containsKey(key);
        }
    }

    /**
     * Map a given key to the given value.
     *
     * @param key
     *            The key to map to the given value.
     * @param value
     *            The value to map to the given key.
     */
    public void put(final K key, final V value) {
        synchronized (map) {
            map.put(key, value);
        }
    }

    /**
     * Return the previously mapped value and map a new value.
     *
     * @param key
     *            The key to map to the given value.
     * @param value
     *            The value to map to the given key.
     * @return The previously mapped value to the given key.
     */
    public V getAndPut(final K key, final V value) {
        synchronized (map) {
            return map.put(key, value);
        }
    }

    /**
     * Copies all of the mappings from the provided map to this cache.
     * These mappings will replace any keys currently in the cache.
     *
     * @param map
     *            The mappings to copy.
     */
    public void putAll(final Map<? extends K, ? extends V> map) {
        synchronized (map) {
            this.map.putAll(map);
        }
    }

    /**
     * Map a given key to the given value if not already mapped.
     *
     * @param key
     *            The key to map to the given value.
     * @param value
     *            The value to map to the given key.
     * @return True if the mapping has been made.
     */
    public boolean putIfAbsent(final K key, final V value) {
        synchronized (map) {
            return map.putIfAbsent(key, value) == null;
        }
    }

    /**
     * Remove a given key from the cache.
     *
     * @param key
     *            The key to remove.
     * @return True if the key has been removed.
     */
    public boolean remove(final K key) {
        synchronized (map) {
            return map.remove(key) != null;
        }
    }

    /**
     * Remove a given key and value from the cache.
     *
     * @param key
     *            The key to remove.
     * @param oldValue
     *            The value to remove.
     * @return True if the key and oldValue has been removed.
     */
    public boolean remove(final K key, final V oldValue) {
        synchronized (map) {
            return map.remove(key, oldValue);
        }
    }

    /**
     * Return the previously mapped value and remove the key.
     *
     * @param key
     *            The key to remove.
     * @return The previously mapped value to the given key.
     */
    public V getAndRemove(final K key) {
        synchronized (map) {
            return map.remove(key);
        }
    }

    /**
     * Remove all of the cached items.
     */
    public void removeAll() {
        synchronized (map) {
            map.clear();
        }
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        synchronized (map) {
            map.clear();
        }
    }

}
