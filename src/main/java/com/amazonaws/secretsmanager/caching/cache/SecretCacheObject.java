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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.secretsmanager.caching.cache.internal.VersionInfo;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Basic secret caching object.
 */
public abstract class SecretCacheObject<T> {

    /** The number of milliseconds to wait after an exception. */
    private static final long EXCEPTION_BACKOFF = 1000;

    /** The growth factor of the backoff duration. */
    private static final long EXCEPTION_BACKOFF_GROWTH_FACTOR = 2;

    /**
     * The maximum number of milliseconds to wait before retrying a failed
     * request.
     */
    private static final long BACKOFF_PLATEAU = EXCEPTION_BACKOFF * 128;

    /**
     * When forcing a refresh using the refreshNow method, a random sleep
     * will be performed using this value.  This helps prevent code from
     * executing a refreshNow in a continuous loop without waiting.
     */
    private static final long FORCE_REFRESH_JITTER_SLEEP = 5000;

    /** The secret identifier for this cached object. */
    protected final String secretId;

    /** A private object to synchronize access to certain methods. */
    protected final Object lock = new Object();

    /** The AWS Secrets Manager client to use for requesting secrets. */
    protected final AWSSecretsManager client;

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
     * The number of exceptions encountered since the last successfully
     * AWS Secrets Manager request.  This is used to calculate an exponential
     * backoff.
     */
    private long exceptionCount = 0;

    /**
     * The time to wait before retrying a failed AWS Secrets Manager request.
     */
    private long nextRetryTime = 0;

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
    public SecretCacheObject(final String secretId,
                             final AWSSecretsManager client,
                             final SecretCacheConfiguration config) {
        this.secretId = secretId;
        this.client = client;
        this.config = config;
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
    protected abstract GetSecretValueResult getSecretValue(T result);

    public abstract boolean equals(Object obj);
    public abstract int hashCode();
    public abstract String toString();

    protected <U extends AmazonWebServiceRequest> U updateUserAgent(U request) {
        request.getRequestClientOptions().appendUserAgent(VersionInfo.USER_AGENT);
        return request;
    }

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
            if (System.currentTimeMillis() >= this.nextRetryTime) {
                return true;
            }
            // Don't keep trying to refresh a secret that previously threw
            // an exception.
            return false;
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
            this.exceptionCount = 0;
        } catch (RuntimeException ex) {
            this.exception = ex;
            // Determine the amount of growth in exception backoff time based on the growth
            // factor and default backoff duration.
            Long growth = 1L;
            if (this.exceptionCount > 0) {
                growth = (long)Math.pow(EXCEPTION_BACKOFF_GROWTH_FACTOR, this.exceptionCount);
            }
            this.exceptionCount += 1;
            growth *= EXCEPTION_BACKOFF;
            // Add in EXCEPTION_BACKOFF time to make sure the random jitter will not reduce
            // the wait time too low.
            Long retryWait = Math.min(EXCEPTION_BACKOFF + growth, BACKOFF_PLATEAU);
            // Use random jitter with the wait time
            retryWait = ThreadLocalRandom.current().nextLong(retryWait / 2, retryWait + 1);
            this.nextRetryTime = System.currentTimeMillis() + retryWait;
        }
    }

    /**
     * Method to clone a List of String
     *
     * @param l
     *        The List of String
     * @return The cloned List of String.
     */
    private List<String> clone(List<String> l) {
        if (null == l) { return null; }
        return new ArrayList<>(l);
    }

    /**
     * Method to clone a ByteBuffer
     *
     * @param b
     *        The ByteBuffer to be cloned.
     * @return The cloned ByteBuffer.
     */
    private ByteBuffer clone(ByteBuffer b) {
        // Nothing to clone, return null.
        if (null == b) { return null; }
        b.rewind();
        ByteBuffer clone = ByteBuffer.allocate(b.remaining());

        if (b.hasArray()) {
            System.arraycopy(b.array(), 0, clone.array(), 0, b.remaining());
        }
        else {
            clone.put(b.duplicate());
            clone.flip();
        }

        return clone;
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
        // When forcing a refresh, always sleep with a random jitter
        // to prevent coding errors that could be calling refreshNow
        // in a loop.
        long sleep = ThreadLocalRandom.current()
                .nextLong(
                        FORCE_REFRESH_JITTER_SLEEP / 2,
                        FORCE_REFRESH_JITTER_SLEEP + 1);
        // Make sure we are not waiting for the next refresh after an
        // exception.  If we are, sleep based on the retry delay of
        // the refresh to prevent a hard loop in attempting to refresh a
        // secret that continues to throw an exception such as AccessDenied.
        if (null != this.exception) {
            long wait = this.nextRetryTime - System.currentTimeMillis();
            sleep = Math.max(wait, sleep);
        }
        Thread.sleep(sleep);

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
    public GetSecretValueResult getSecretValue() {
        synchronized (lock) {
            refresh();
            if (null == this.data) {
                if (null != this.exception) { throw this.exception; }
            }
            GetSecretValueResult gsv = this.getSecretValue(this.getResult());

            // If there is no cached result, return null.
            if (null == gsv) { return null; }

            // We want to clone the result to prevent callers from modifying
            // the cached data.
            gsv = gsv.clone();
            // The prior clone did not perform a deep clone of all objects.
            // Handle cloning the byte buffer it one exists.
            gsv.setSecretBinary(clone(gsv.getSecretBinary()));
            gsv.setVersionStages(clone(gsv.getVersionStages()));
            return gsv;
        }
    }

}
