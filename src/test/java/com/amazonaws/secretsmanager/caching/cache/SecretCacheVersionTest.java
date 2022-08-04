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

import org.testng.Assert;
import org.testng.annotations.Test;

public class SecretCacheVersionTest {
    @Test
    public void cacheVersionEqualsTest() {
        SecretCacheVersion i1 = new SecretCacheVersion("test", "version", null, null);
        SecretCacheVersion i2 = new SecretCacheVersion("test", "version", null, null);
        SecretCacheVersion i3 = new SecretCacheVersion("test3", "version", null, null);
        SecretCacheVersion i4 = new SecretCacheVersion("test", "version4", null, null);
        Assert.assertEquals(i1, i2);
        Assert.assertNotEquals(i1, null);
        Assert.assertNotEquals(i1, i3);
        Assert.assertNotEquals(i1, i4);
        Assert.assertFalse(i1.equals(null));
        Assert.assertEquals(i1.hashCode(), i2.hashCode());
        Assert.assertNotEquals(i1.hashCode(), i3.hashCode());
        Assert.assertNotEquals(i1.hashCode(), i4.hashCode());
        Assert.assertEquals(i1.toString(), "SecretCacheVersion: test version");
    }
}
