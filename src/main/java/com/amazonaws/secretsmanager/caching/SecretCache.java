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

import com.amazonaws.secretsmanager.caching.cache.LRUCache;
import com.amazonaws.secretsmanager.caching.cache.SecretCacheItem;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

import java.nio.ByteBuffer;

/**
 * Provides the primary entry-point to the AWS Secrets Manager client cache SDK.
 * Most people will want to use either
 * {@link #getSecretString(String)} or
 * {@link #getSecretBinary(String)} to retrieve a secret from the cache.
 *
 * <P>
 * The core concepts (and classes) in this SDK are:
 * <ul>
 * <li>{@link SecretCache}
 * <li>{@link SecretCacheConfiguration}
 * </ul>
 *
 * <p>
 * {@link SecretCache} provides an in-memory cache for secrets requested from
 * AWS Secrets Manager.
 *
 */
public class SecretCache implements AutoCloseable {

    /** The cached secret items. */
    private final LRUCache<String, SecretCacheItem> cache;

    /** The cache configuration. */
    private final SecretCacheConfiguration config;

    /** The AWS Secrets Manager client to use when requesting secrets. */
    private final AWSSecretsManager client;

    /**
     * Constructs a new secret cache using the standard AWS Secrets Manager client with default options.
     */
    public SecretCache() {
        this(AWSSecretsManagerClientBuilder.standard());
    }


    /**
     * Constructs a new secret cache using an AWS Secrets Manager client created using the
     * provided builder.
     *
     * @param builder
     *        The builder to use for creating the AWS Secrets Manager client.
     */
    public SecretCache(AWSSecretsManagerClientBuilder builder) {
        this(null == builder ?
                AWSSecretsManagerClientBuilder.standard().build() :
                builder.build());
    }

    /**
     * Constructs a new secret cache using the provided AWS Secrets Manager client.
     *
     * @param client
     *        The AWS Secrets Manager client to use for requesting secret values.
     */
    public SecretCache(AWSSecretsManager client) {
        this(new SecretCacheConfiguration().withClient(client));
    }

    /**
     * Constructs a new secret cache using the provided cache configuration.
     *
     * @param config
     *        The secret cache configuration.
     */
    public SecretCache(SecretCacheConfiguration config) {
        if (null == config) { config = new SecretCacheConfiguration(); }
        this.cache = new LRUCache<String, SecretCacheItem>(config.getMaxCacheSize());
        this.config = config;
        this.client = config.getClient() != null ? config.getClient() :
                AWSSecretsManagerClientBuilder.standard().build();
    }

    /**
     * Method to retrieve the cached secret item.
     *
     * @param secretId
     *        The identifier for the secret being requested.
     * @return The cached secret item
     */
    private SecretCacheItem getCachedSecret(final String secretId) {
        SecretCacheItem secret = this.cache.get(secretId);
        if (null == secret) {
            this.cache.putIfAbsent(secretId,
                    new SecretCacheItem(secretId, this.client, this.config));
            secret = this.cache.get(secretId);
        }
        return secret;
    }

    /**
     * Method to retrieve a string secret from AWS Secrets Manager.
     *
     * @param secretId
     *        The identifier for the secret being requested.
     * @return The string secret
     */
    public String getSecretString(final String secretId) {
        SecretCacheItem secret = this.getCachedSecret(secretId);
        GetSecretValueResult gsv = secret.getSecretValue();
        if (null == gsv) { return null; }
        return gsv.getSecretString();
    }

    /**
     * Method to retrieve a binary secret from AWS Secrets Manager.
     *
     * @param secretId
     *        The identifier for the secret being requested.
     * @return The binary secret
     */
    public ByteBuffer getSecretBinary(final String secretId) {
        SecretCacheItem secret = this.getCachedSecret(secretId);
        GetSecretValueResult gsv = secret.getSecretValue();
        if (null == gsv) { return null; }
        return gsv.getSecretBinary();
    }

    /**
     * Method to force the refresh of a cached secret state.
     *
     * @param secretId
     *        The identifier for the secret being refreshed.
     * @return True if the refresh completed without error.
     * @throws InterruptedException
     *             If the thread is interrupted while waiting for the refresh.
     */
    public boolean refreshNow(final String secretId) throws InterruptedException {
        SecretCacheItem secret = this.getCachedSecret(secretId);
        return secret.refreshNow();
    }

    /**
     * Method to close the cache.
     */
    @Override
    public void close() {
        this.cache.clear();
    }

}
