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

import java.util.HashMap;

import org.testng.annotations.Test;
import org.testng.Assert;

public class LRUCacheTest {

    @Test
    public void putIntTest() {
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        cache.put(1, 1);
        Assert.assertTrue(cache.containsKey(1));
        Assert.assertEquals(cache.get(1), new Integer(1));
    }

    @Test
    public void removeTest() {
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        cache.put(1, 1);
        Assert.assertNotNull(cache.get(1));
        Assert.assertTrue(cache.remove(1));
        Assert.assertNull(cache.get(1));
        Assert.assertFalse(cache.remove(1));
        Assert.assertNull(cache.get(1));
    }

    @Test
    public void removeAllTest() {
        int max = 100;
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        for (int n = 0; n < max; ++n) {
            cache.put(n, n);
        }
        cache.removeAll();
        for (int n = 0; n < max; ++n) {
            Assert.assertNull(cache.get(n));
        }
    }

    @Test
    public void clearTest() {
        int max = 100;
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        for (int n = 0; n < max; ++n) {
            cache.put(n, n);
        }
        cache.clear();
        for (int n = 0; n < max; ++n) {
            Assert.assertNull(cache.get(n));
        }
    }

    @Test
    public void getAndRemoveTest() {
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        cache.put(1, 1);
        Assert.assertNotNull(cache.get(1));
        Assert.assertEquals(cache.getAndRemove(1), new Integer(1));
        Assert.assertNull(cache.get(1));
    }

    @Test
    public void removeWithValueTest() {
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        cache.put(1, 1);
        Assert.assertNotNull(cache.get(1));
        Assert.assertFalse(cache.remove(1, 2));
        Assert.assertNotNull(cache.get(1));
        Assert.assertTrue(cache.remove(1, 1));
        Assert.assertNull(cache.get(1));
    }

    @Test
    public void putStringTest() {
        LRUCache<String, String> cache = new LRUCache<String, String>();
        cache.put("a", "a");
        Assert.assertTrue(cache.containsKey("a"));
        Assert.assertEquals(cache.get("a"), "a");
    }

    @Test
    public void putIfAbsentTest() {
        LRUCache<String, String> cache = new LRUCache<String, String>();
        Assert.assertTrue(cache.putIfAbsent("a", "a"));
        Assert.assertFalse(cache.putIfAbsent("a", "a"));
    }

    @Test
    public void maxSizeTest() {
        int maxCache = 5;
        int max = 100;
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>(maxCache);
        for (int n = 0; n < max; ++n) {
            cache.put(n, n);
        }
        for (int n = 0; n < max - maxCache; ++n) {
            Assert.assertNull(cache.get(n));
        }
        for (int n = max - maxCache; n < max; ++n) {
            Assert.assertNotNull(cache.get(n));
            Assert.assertEquals(cache.get(n), new Integer(n));
        }
    }

    @Test
    public void maxSizeLRUTest() {
        int maxCache = 5;
        int max = 100;
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>(maxCache);
        for (int n = 0; n < max; ++n) {
            cache.put(n, n);
            Assert.assertEquals(cache.get(0), new Integer(0));
        }
        for (int n = 1; n < max - maxCache; ++n) {
            Assert.assertNull(cache.get(n));
        }
        for (int n = max - maxCache + 1; n < max; ++n) {
            Assert.assertNotNull(cache.get(n));
            Assert.assertEquals(cache.get(n), new Integer(n));
        }
        Assert.assertEquals(cache.get(0), new Integer(0));
    }

    @Test
    public void getAndPutTest() {
        int max = 100;
        Integer prev = null;
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        for (int n = 0; n < max; ++n) {
            Assert.assertEquals(cache.getAndPut(1, n), prev);
            prev = n;
        }
    }

    @Test
    public void putAllTest() {
        int max = 100;
        HashMap<Integer, Integer> m = new HashMap<Integer, Integer>();
        LRUCache<Integer, Integer> cache = new LRUCache<Integer, Integer>();
        for (int n = 0; n < max; ++n) {
            m.put(n, n);
        }
        cache.putAll(m);
        for (int n = 0; n < max; ++n) {
            Assert.assertEquals(cache.get(n), new Integer(n));
        }
    }

}
