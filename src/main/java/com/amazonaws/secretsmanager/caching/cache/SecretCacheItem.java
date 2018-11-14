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

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.DescribeSecretRequest;
import com.amazonaws.services.secretsmanager.model.DescribeSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The cached secret item which contains information from the DescribeSecret
 * request to AWS Secrets Manager along with any associated GetSecretValue
 * results.
 *
 */
public class SecretCacheItem extends SecretCacheObject<DescribeSecretResult> {

    /** The cached secret value versions for this cached secret. */
    private LRUCache<String, SecretCacheVersion> versions = new LRUCache<String, SecretCacheVersion>(10);

    /**
     * The next scheduled refresh time for this item.  Once the item is accessed
     * after this time, the item will be synchronously refreshed.
     */
    private long nextRefreshTime = 0;

    /**
     * Construct a new cached item for the secret.
     *
     * @param secretId
     *            The secret identifier.  This identifier could be the full ARN
     *            or the friendly name for the secret.
     * @param client
     *            The AWS Secrets Manager client to use for requesting the secret.
     * @param config
     *            Cache configuration.
     */
    public SecretCacheItem(final String secretId,
                           final AWSSecretsManager client,
                           final SecretCacheConfiguration config) {
        super(secretId, client, config);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SecretCacheItem) {
            return Objects.equals(this.secretId, ((SecretCacheItem)obj).secretId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return String.format("%s", this.secretId).hashCode();
    }

    @Override
    public String toString() {
        return String.format("SecretCacheItem: %s", this.secretId);
    }

    /**
     * Determine if the secret object should be refreshed.  This method
     * extends the base class functionality to check if a refresh is
     * needed based on the configured TTL for the item.
     *
     * @return True if the secret item should be refreshed.
     */
    @Override
    protected boolean isRefreshNeeded() {
        if (super.isRefreshNeeded()) { return true; }
        if (null != this.exception) { return false; }
        if (System.currentTimeMillis() >= this.nextRefreshTime) {
            return true;
        }
        return false;
    }

    /**
     * Execute the logic to perform the actual refresh of the item.
     *
     * @return The result from AWS Secrets Manager for the refresh.
     */
    @Override
    protected DescribeSecretResult executeRefresh() {
        DescribeSecretResult describeSecretResult = client.describeSecret(
                updateUserAgent(new DescribeSecretRequest()
                        .withSecretId(this.secretId)));
        long ttl = this.config.getCacheItemTTL();
        this.nextRefreshTime = System.currentTimeMillis() +
                ThreadLocalRandom.current().nextLong(ttl / 2,ttl + 1) ;

        return describeSecretResult;
    }

    /**
     * Return the secret version based on the current state of the secret.
     *
     * @param describeResult
     *            The result of the Describe Secret request to AWS Secrets Manager.
     * @return The cached secret version.
     */
    private SecretCacheVersion getVersion(DescribeSecretResult describeResult) {
        if (null == describeResult) { return null; }
        if (null == describeResult.getVersionIdsToStages()) { return null; }
        Optional<String> currentVersionId = describeResult.getVersionIdsToStages().entrySet()
                .stream()
                .filter(Objects::nonNull)
                .filter(x -> x.getValue() != null)
                .filter(x -> x.getValue().contains(this.config.getVersionStage()))
                .map(x -> x.getKey())
                .findFirst();
        if (currentVersionId.isPresent()) {
            SecretCacheVersion version = versions.get(currentVersionId.get());
            if (null == version) {
                versions.putIfAbsent(currentVersionId.get(),
                        new SecretCacheVersion(this.secretId, currentVersionId.get(), this.client, this.config));
                version = versions.get(currentVersionId.get());
            }
            return version;
        }
        return null;
    }

    /**
     * Return the cached result from AWS Secrets Manager for GetSecretValue.
     *
     * @param describeResult
     *            The result of the Describe Secret request to AWS Secrets Manager.
     * @return The cached GetSecretValue result.
     */
    @Override
    protected GetSecretValueResult getSecretValue(DescribeSecretResult describeResult) {
        SecretCacheVersion version = getVersion(describeResult);
        if (null == version) { return null; }
        return version.getSecretValue();
    }

}
