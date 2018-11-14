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
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

import java.util.Objects;

/**
 * The cached secret version item which contains information from the
 * GetSecretValue AWS Secrets Manager request.
 */
public class SecretCacheVersion extends SecretCacheObject<GetSecretValueResult> {

    /** The version identifier to use when requesting the secret value. */
    private final String versionId;

    /** The calculated hash for this item based on the secret and version. */
    private final int hash;

    /**
     * Construct a new cached version for the secret.
     *
     * @param secretId
     *            The secret identifier.  This identifier could be the full ARN
     *            or the friendly name for the secret.
     * @param versionId
     *            The version identifier that should be used when requesting the
     *            secret value from AWS Secrets Manager.
     * @param client
     *            The AWS Secrets Manager client to use for requesting the secret.
     * @param config
     *            The secret cache configuration.
     */
    public SecretCacheVersion(final String secretId,
                              final String versionId,
                              final AWSSecretsManager client,
                              final SecretCacheConfiguration config) {
        super(secretId, client, config);
        this.versionId = versionId;
        hash = String.format("%s %s", secretId, versionId).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SecretCacheVersion) {
            return Objects.equals(this.secretId,((SecretCacheVersion)obj).secretId) &&
                    Objects.equals(this.versionId, ((SecretCacheVersion)obj).versionId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return String.format("SecretCacheVersion: %s %s", secretId, versionId);
    }

    /**
     * Execute the logic to perform the actual refresh of the item.
     *
     * @return The result from AWS Secrets Manager for the refresh.
     */
    @Override
    protected GetSecretValueResult executeRefresh() {
        return client.getSecretValue(
                updateUserAgent(new GetSecretValueRequest()
                        .withSecretId(this.secretId).withVersionId(this.versionId)));
    }

    /**
     * Return the cached result from AWS Secrets Manager for GetSecretValue.
     *
     * @param gsvResult
     *            The result of the Get Secret Value request to AWS Secrets Manager.
     * @return The cached GetSecretValue result.
     */
    @Override
    protected GetSecretValueResult getSecretValue(GetSecretValueResult gsvResult) {
        return gsvResult;
    }

}
