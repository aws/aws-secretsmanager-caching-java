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

import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.awssdk.retries.api.internal.backoff.ExponentialDelayWithJitter;
import software.amazon.awssdk.retries.api.internal.backoff.FixedDelayWithJitter;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Basic secret caching object.
 */
public abstract class SecretCacheObject<T> {

    /** The duration to wait after an exception. */
    private static final Duration BACKOFF_MIN = Duration.ofSeconds(1);

    /**
     * The maximum duration to back off to.
     */
    private static final Duration BACKOFF_MAX = Duration.ofSeconds(20);

    /**
     * Backoff strategy with jitter for retries on errors.
     * Mimics the backoff strategy of the AWS SDK for Java with unlimited attempts.
     */
    private static ExponentialDelayWithJitter refreshStrategy = new ExponentialDelayWithJitter(ThreadLocalRandom::current,
            BACKOFF_MIN, BACKOFF_MAX);

    /** The secret identifier for this cached object. */
    protected final String secretId;

    /** A private object to synchronize access to certain methods. */
    protected final Object lock = new Object();

    /** The AWS Secrets Manager client to use for requesting secrets. */
    protected final SecretsManagerClient client;

    /** The Secret Cache Configuration. */
    protected final SecretCacheConfiguration config;

    /** A flag to indicate a refresh is needed. */
    private boolean refreshNeeded = true;

    /** The result of the last AWS Secrets Manager request for this item. */
    private Object data = null;

    /**
     * If the last request to AWS Secrets Manager resulted in an exception,
     * that exception will be thrown back to the caller when requesting
     * secret data.
     */
    protected RuntimeException exception = null;

    /**
     * The number of attempts encountered since the last successful
     * AWS Secrets Manager request. This is used to calculate an exponential
     * backoff. Starts at 1.
     */
    private int attempts = 1;

    /**
     * When forcing a refresh, always sleep with a random jitter
     * to prevent coding errors that could be calling refreshNow
     * in a loop.
     */
    private FixedDelayWithJitter refreshNowRetryStrategy;

    /**
     * The time to wait before retrying a failed AWS Secrets Manager request.
     */
    private Instant nextRetryTime = Instant.ofEpochMilli(0);

    /**
     * Construct a new cached item for the secret.
     *
     * @param secretId
     *            The secret identifier.  This identifier could be the full ARN
     *            or the friendly name for the secret.
     * @param client
     *            The AWS Secrets Manager client to use for requesting the secret.
     * @param config
     *            The secret cache configuration.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2")
    public SecretCacheObject(final String secretId,
                             final SecretsManagerClient client,
                             final SecretCacheConfiguration config) {
        this.secretId = secretId;
        this.client = client;
        this.config = config;
        refreshNowRetryStrategy = new FixedDelayWithJitter(ThreadLocalRandom::current,
                this.config.getForceRefreshJitter());
    }

    /**
     * Execute the actual refresh of the cached secret state.
     *
     * @return The result of the refresh
     */
    protected abstract T executeRefresh();

    /**
     * Execute the actual refresh of the cached secret state.
     *
     * @param result
     *            The AWS Secrets Manager result for the secret state.
     * @return The cached GetSecretValue result based on the current
     *         cached state.
     */
    protected abstract GetSecretValueResponse getSecretValue(T result);

    public abstract boolean equals(Object obj);
    public abstract int hashCode();
    public abstract String toString();

    /**
     * Return the typed result object
     *
     * @return the result object
     */
    @SuppressWarnings("unchecked")
    private T getResult() {
        if (null != this.config.getCacheHook()) {
            return (T)this.config.getCacheHook().get(this.data);
        }
        return (T)this.data;
    }

    /**
     * Store the result data.
     */
    private void setResult(T result) {
        if (null != this.config.getCacheHook()) {
            this.data = this.config.getCacheHook().put(result);
        } else {
            this.data = result;
        }
    }

    /**
     * Determine if the secret object should be refreshed.
     *
     * @return True if the secret item should be refreshed.
     */
    protected boolean isRefreshNeeded() {
        if (this.refreshNeeded) { return true; }
        if (null != this.exception) {
            // If we encountered an exception on the last attempt
            // we do not want to keep retrying without a pause between
            // the refresh attempts.
            //
            // If we have exceeded our backoff time we will refresh
            // the secret now.
            // Don't keep trying to refresh a secret that previously threw
            // an exception.
            return Instant.now().isAfter(this.nextRetryTime);
        }
        return false;
    }

    /**
     * Refresh the cached secret state only when needed.
     */
    private void refresh() {
        if (!this.isRefreshNeeded()) { return; }
        this.refreshNeeded = false;
        try {
            this.setResult(this.executeRefresh());
            this.exception = null;
            this.attempts = 1;
        } catch (RuntimeException ex) {
            this.exception = ex;
            // Increment before computing delay. Otherwise the retry for attempts = 1 is immediate.
            this.attempts++;
            Duration retryWait = refreshStrategy.computeDelay(this.attempts);
            this.nextRetryTime = Instant.now().plus(retryWait);
        }
    }

    /**
     * Method to force the refresh of a cached secret state.
     *
     * @return True if the refresh completed without error.
     * @throws InterruptedException
     *             If the thread is interrupted while waiting for the refresh.
     */
    public boolean refreshNow() throws InterruptedException {
        this.refreshNeeded = true;

        // Attempts is irrelevant for a fixed delay retry strategy
        Duration sleep = this.refreshNowRetryStrategy.computeDelay(1);
        if (null != this.exception) {
            // Make sure we are not waiting for the next refresh after an
            // exception. If we are, sleep based on the retry delay of
            // the refresh to prevent a hard loop in attempting to refresh a
            // secret that continues to throw an exception such as AccessDenied.
            Duration wait = Duration.between(
                    this.nextRetryTime,
                    java.time.Instant.now());
            // pick the max.
            sleep = sleep.compareTo(wait) >= 0 ? sleep : wait;
        }
        Thread.sleep(sleep.toMillis());

        // Perform the requested refresh
        synchronized (lock) {
            refresh();
            return (null == this.exception);
        }
    }

    /**
     * Return the cached result from AWS Secrets Manager for GetSecretValue.
     *
     * @return The cached GetSecretValue result.
     */
    public GetSecretValueResponse getSecretValue() {
        synchronized (lock) {
            refresh();
            if (null == this.data) {
                if (null != this.exception) { throw this.exception; }
            }

            return this.getSecretValue(this.getResult());
        }
    }

}
