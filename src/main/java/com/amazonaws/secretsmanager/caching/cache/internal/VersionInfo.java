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
 * This class specifies the versioning system for the AWS SecretsManager caching client.
 */
public class VersionInfo {
    // incremented for design changes that break backward compatibility.
    public static final String VERSION_NUM = "1";
    // incremented for major changes to the implementation
    public static final String MAJOR_REVISION_NUM = "1";
    // incremented for minor changes to the implementation
    public static final String MINOR_REVISION_NUM = "0";
    // incremented for releases containing an immediate bug fix.
    public static final String BUGFIX_REVISION_NUM = "0";

    public static final String RELEASE_VERSION = VERSION_NUM + "." + MAJOR_REVISION_NUM + "." + MINOR_REVISION_NUM
            + "." + BUGFIX_REVISION_NUM;

    public static final String USER_AGENT = "AwsSecretCache/" + RELEASE_VERSION;

    private VersionInfo(){}
}