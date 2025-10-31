package com.amazonaws.secretsmanager.caching;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SecretCacheConfigurationTest {
    @Deprecated
    @Test
    public void getCacheItemTTLIsPositive() {
        SecretCacheConfiguration c = new SecretCacheConfiguration();
        Assert.assertTrue(c.getCacheItemTTL() > 0, c.getCacheItemTTL() + " is not greater than zero");
    }
}
