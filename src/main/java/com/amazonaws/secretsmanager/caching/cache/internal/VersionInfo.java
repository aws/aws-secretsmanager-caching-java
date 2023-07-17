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

package com.amazonaws.secretsmanager.caching.cache.internal;

/**
 * This class specifies the versioning system for the AWS SecretsManager caching
 * client.
 */
public class VersionInfo {
    /**
     * User agent
     */
    public static final String USER_AGENT = "AwsSecretCache/2.0.0";

    private VersionInfo() {
    }
}