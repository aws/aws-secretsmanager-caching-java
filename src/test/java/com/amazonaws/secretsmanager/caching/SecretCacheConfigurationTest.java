package com.amazonaws.secretsmanager.caching;

import org.powermock.api.mockito.PowerMockito;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

/**
 * SecretCacheConfigurationTest
 */
public class SecretCacheConfigurationTest {

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }

    @Test
    public void findCacheItemTTLByDefaultTest() {
        // Call default constructor
        SecretCacheConfiguration secretCacheConfiguration = new SecretCacheConfiguration();

        // Verify that cacheItemTTL set the default
        Assert.assertEquals(secretCacheConfiguration.getCacheItemTTL(),
                SecretCacheConfiguration.DEFAULT_CACHE_ITEM_TTL);
    }

    @Test
    public void findCacheItemTTLEnvTest() throws Exception {
        // Provisioning for test
        SecretCacheConfiguration spy = PowerMockito.spy(new SecretCacheConfiguration());
        PowerMockito.whenNew(SecretCacheConfiguration.class).withNoArguments()
                .thenReturn(spy);
        PowerMockito.doReturn(TimeUnit.HOURS.toMillis(12)).when(spy, "getCacheItemTTL");

        // Verify that cacheItemTTL set from the system property
        Assert.assertEquals(spy.getCacheItemTTL(),
                TimeUnit.HOURS.toMillis(12));
    }

    @Test
    public void findCacheItemTTLPropertyTest() {
        // Provisioning for test
        System.setProperty(SecretCacheConfiguration.CACHE_ITEM_TTL_PROP,
                String.valueOf(TimeUnit.HOURS.toMillis(24)));

        // Call default constructor
        SecretCacheConfiguration secretCacheConfiguration = new SecretCacheConfiguration();

        // Verify that cacheItemTTL set from the environment variable
        Assert.assertEquals(secretCacheConfiguration.getCacheItemTTL(),
                TimeUnit.HOURS.toMillis(24));
    }
}
