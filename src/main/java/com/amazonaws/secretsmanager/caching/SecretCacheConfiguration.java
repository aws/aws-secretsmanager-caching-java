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

package com.amazonaws.secretsmanager.caching;

import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;


/**
 * Cache configuration options such as max cache size, ttl for cached items, etc.
 *
 */
public class SecretCacheConfiguration {

    /** The default cache size. */
    public static final int DEFAULT_MAX_CACHE_SIZE = 1024;

    /** The default TTL for an item stored in cache before access causing a refresh. */
    public static final long DEFAULT_CACHE_ITEM_TTL = TimeUnit.HOURS.toMillis(1);

    /** The default version stage to use when retrieving secret values. */
    public static final String DEFAULT_VERSION_STAGE = "AWSCURRENT";

    /** The client this cache instance will use for accessing AWS Secrets Manager. */
    private SecretsManagerClient client = null;

    /** Used to hook in-memory cache updates. */
    private SecretCacheHook cacheHook = null;

    /**
     * The maximum number of cached secrets to maintain before evicting secrets that
     * have not been accessed recently.
     */
    private int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;

    /**
     * The number of milliseconds that a cached item is considered valid before
     * requiring a refresh of the secret state.  Items that have exceeded this
     * TTL will be refreshed synchronously when requesting the secret value.  If
     * the synchronous refresh failed, the stale secret will be returned.
     */
    private long cacheItemTTL = DEFAULT_CACHE_ITEM_TTL;

    /**
     * The version stage that will be used when requesting the secret values for
     * this cache.
     */
    private String versionStage = DEFAULT_VERSION_STAGE;

    /**
     * Default constructor for the SecretCacheConfiguration object.
     *
     */
    public SecretCacheConfiguration() {
    }

    /**
     * Returns the AWS Secrets Manager client that is used for requesting secret values.
     *
     * @return The AWS Secrets Manager client.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP")
    public SecretsManagerClient getClient() {
        return client;
    }


    /**
     * Sets the AWS Secrets Manager client that should be used by the cache for requesting
     * secrets.
     *
     * @param client
     *            The AWS Secrets Manager client.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2")
    public void setClient(SecretsManagerClient client) {
        this.client = client;
    }

    /**
     * Sets the AWS Secrets Manager client that should be used by the cache for requesting
     * secrets.
     *
     * @param client
     *            The AWS Secrets Manager client.
     * @return The updated ClientConfiguration object with the new client setting.
     */
    public SecretCacheConfiguration withClient(SecretsManagerClient client) {
        this.setClient(client);
        return this;
    }


    /**
     * Returns the interface used to hook in-memory cache updates.
     *
     * @return The object used to hook in-memory cache updates.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP")
    public SecretCacheHook getCacheHook() {
        return cacheHook;
    }


    /**
     * Sets the interface used to hook the in-memory cache.
     *
     * @param cacheHook
     *            The interface used to hook the in-memory cache.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2")
    public void setCacheHook(SecretCacheHook cacheHook) {
        this.cacheHook = cacheHook;
    }


    /**
     * Sets the interface used to hook the in-memory cache.
     *
     * @param cacheHook
     *            The interface used to hook in-memory cache.
     * @return The updated ClientConfiguration object with the new setting.
     */
    public SecretCacheConfiguration withCacheHook(SecretCacheHook cacheHook) {
        this.setCacheHook(cacheHook);
        return this;
    }


    /**
     * Returns the max cache size that should be used for creating the cache.
     *
     * @return The max cache size.
     */
    public int getMaxCacheSize() {
        return this.maxCacheSize;
    }

    /**
     * Sets the max cache size.
     *
     * @param maxCacheSize
     *            The max cache size.
     */
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * Sets the max cache size.
     *
     * @param maxCacheSize
     *            The max cache size.
     * @return The updated ClientConfiguration object with the new max setting.
     */
    public SecretCacheConfiguration withMaxCacheSize(int maxCacheSize) {
        this.setMaxCacheSize(maxCacheSize);
        return this;
    }

    /**
     * Returns the TTL for the cached items.
     *
     * @return The TTL in milliseconds before refreshing cached items.
     */
    public long getCacheItemTTL() {
        return this.cacheItemTTL;
    }

    /**
     * Sets the TTL in milliseconds for the cached items.  Once cached items exceed this
     * TTL, the item will be refreshed using the AWS Secrets Manager client.
     *
     * @param cacheItemTTL
     *            The TTL for cached items before requiring a refresh.
     */
    public void setCacheItemTTL(long cacheItemTTL) {
        this.cacheItemTTL = cacheItemTTL;
    }

    /**
     * Sets the TTL in milliseconds for the cached items.  Once cached items exceed this
     * TTL, the item will be refreshed using the AWS Secrets Manager client.
     *
     * @param cacheItemTTL
     *            The TTL for cached items before requiring a refresh.
     * @return The updated ClientConfiguration object with the new TTL setting.
     */
    public SecretCacheConfiguration withCacheItemTTL(long cacheItemTTL) {
        this.setCacheItemTTL(cacheItemTTL);
        return this;
    }

    /**
     * Returns the version stage that is used for requesting secret values.
     *
     * @return The version stage used in requesting secret values.
     */
    public String getVersionStage() {
        return this.versionStage;
    }

    /**
     * Sets the version stage that should be used for requesting secret values
     * from AWS Secrets Manager
     *
     * @param versionStage
     *            The version stage used for requesting secret values.
     */
    public void setVersionStage(String versionStage) {
        this.versionStage = versionStage;
    }

    /**
     * Sets the version stage that should be used for requesting secret values
     * from AWS Secrets Manager
     *
     * @param versionStage
     *            The version stage used for requesting secret values.
     * @return The updated ClientConfiguration object with the new version stage setting.
     */
    public SecretCacheConfiguration withVersionStage(String versionStage) {
        this.setVersionStage(versionStage);
        return this;
    }

}
