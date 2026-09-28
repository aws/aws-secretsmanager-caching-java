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

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.amazonaws.secretsmanager.caching.cache.LRUCache;
import com.amazonaws.secretsmanager.caching.cache.SecretCacheItem;
import com.amazonaws.secretsmanager.caching.cache.internal.VersionInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;

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
    private final SecretsManagerClient client;

    /**
     * Runs the API call attempt timeouts of every client the cache builds, unless its
     * builder sets its own executor. Without it, each client would start its own pool
     * of SDK timer threads, which only stop when the client is closed, and the cache
     * does not close its client. The SDK never shuts down an executor it is given.
     */
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = timeoutExecutor();

    /**
     * Constructs a new secret cache using the standard AWS Secrets Manager client
     * with default options, and
     * {@link SecretCacheConfiguration#DEFAULT_API_CALL_ATTEMPT_TIMEOUT}.
     */
    public SecretCache() {
        this(SecretsManagerClient.builder());
    }

    /**
     * Constructs a new secret cache using an AWS Secrets Manager client created
     * using the
     * provided builder. Unless the builder sets its own API call attempt timeout,
     * {@link SecretCacheConfiguration#DEFAULT_API_CALL_ATTEMPT_TIMEOUT} is used. The
     * timeout, user agent and, unless the builder sets its own scheduled executor, the
     * cache's shared timeout executor are written into the builder itself, so other
     * clients later built from it get them too. Do not shut that executor down; every
     * cache uses it.
     *
     * @param builder The builder to use for creating the AWS Secrets Manager
     *                client.
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", 
        justification = "Delegates to constructor that validates before field initialization")
    public SecretCache(SecretsManagerClientBuilder builder) {
        this(new SecretCacheConfiguration().withClient(builder
                .overrideConfiguration(withCacheDefaults(builder.overrideConfiguration()))
                .build()));
    }

    /**
     * Constructs a new secret cache using the provided AWS Secrets Manager client.
     *
     * @param client The AWS Secrets Manager client to use for requesting secret
     *               values.
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", 
        justification = "Delegates to constructor that validates before field initialization")
    public SecretCache(SecretsManagerClient client) {
        this(new SecretCacheConfiguration().withClient(client));
    }

    /**
     * Constructs a new secret cache using the provided cache configuration. If the
     * configuration has no client, the cache builds one with
     * {@link SecretCacheConfiguration#DEFAULT_API_CALL_ATTEMPT_TIMEOUT}.
     *
     * @param config The secret cache configuration.
     * @throws IllegalArgumentException if both a custom client and postQuantumTlsEnabled 
     *                                  are specified in the configuration.
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", 
        justification = "Validation occurs before any field initialization")
    public SecretCache(SecretCacheConfiguration config) {
        if (null == config) {
            config = new SecretCacheConfiguration();
        }

        if (config.getClient() != null && config.isPostQuantumTlsEnabled()) {
            throw new IllegalArgumentException(
                "Cannot specify both a custom client and postQuantumTlsEnabled. " +
                "To use PQTLS, omit the custom client or configure your client with PQTLS support.");
        }

        this.cache = new LRUCache<String, SecretCacheItem>(config.getMaxCacheSize());
        this.config = config;
        if (config.getClient() != null) {
            this.client = config.getClient();
        } else {
            SecretsManagerClientBuilder builder = SecretsManagerClient.builder();
            
            if (config.isPostQuantumTlsEnabled()) {
                builder.httpClient(AwsCrtHttpClient.builder()
                    .postQuantumTlsEnabled(true)
                    .build());
            }
        
            this.client = builder.overrideConfiguration(
                    withCacheDefaults(ClientOverrideConfiguration.builder().build())).build();
        }
    }

    /**
     * Adds the cache's user agent suffix to the given override configuration and,
     * unless it already sets them, the default API call attempt timeout and the
     * cache's shared timeout executor.
     *
     * @param override The override configuration of the client being built.
     * @return The override configuration to build the client with.
     */
    private static ClientOverrideConfiguration withCacheDefaults(ClientOverrideConfiguration override) {
        ClientOverrideConfiguration.Builder builder = override.toBuilder()
                .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX, VersionInfo.USER_AGENT);
        if (!override.apiCallAttemptTimeout().isPresent()) {
            builder.apiCallAttemptTimeout(SecretCacheConfiguration.DEFAULT_API_CALL_ATTEMPT_TIMEOUT);
        }
        if (!override.scheduledExecutorService().isPresent()) {
            builder.scheduledExecutorService(TIMEOUT_EXECUTOR);
        }
        return builder.build();
    }

    /**
     * Creates the shared timeout executor. Its one daemon thread is started by the
     * first timed request and exits after a minute with nothing to do, so an
     * application server can unload the application. Cancelled timeouts are removed
     * straight away.
     *
     * @return The shared timeout executor.
     */
    private static ScheduledExecutorService timeoutExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "aws-secretsmanager-caching-timeout");
            thread.setDaemon(true);
            // Don't keep the class loader of whichever application thread happened to start it.
            thread.setContextClassLoader(SecretCache.class.getClassLoader());
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        // The keep-alive must be set first: it is 0 by default on Java 8, which
        // allowCoreThreadTimeOut rejects.
        executor.setKeepAliveTime(1, TimeUnit.MINUTES);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Method to retrieve the cached secret item.
     *
     * @param secretId The identifier for the secret being requested.
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
     * @param secretId The identifier for the secret being requested.
     * @return The string secret
     */
    public String getSecretString(final String secretId) {
        SecretCacheItem secret = this.getCachedSecret(secretId);
        GetSecretValueResponse gsv = secret.getSecretValue();
        if (null == gsv) {
            return null;
        }
        return gsv.secretString();
    }

    /**
     * Method to retrieve a binary secret from AWS Secrets Manager.
     *
     * @param secretId The identifier for the secret being requested.
     * @return The binary secret
     */
    public ByteBuffer getSecretBinary(final String secretId) {
        SecretCacheItem secret = this.getCachedSecret(secretId);
        GetSecretValueResponse gsv = secret.getSecretValue();
        if (null == gsv) {
            return null;
        }
        return gsv.secretBinary().asByteBuffer();
    }

    /**
     * Method to force the refresh of a cached secret state.
     *
     * @param secretId The identifier for the secret being refreshed.
     * @return True if the refresh completed without error.
     * @throws InterruptedException If the thread is interrupted while waiting for
     *                              the refresh.
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
