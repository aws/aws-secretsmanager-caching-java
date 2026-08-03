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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class specifies the versioning system for the AWS SecretsManager caching
 * client.
 *
 * <p>
 * The library version is defined in a single place ({@code pom.xml}) and made
 * available at runtime through the {@code version.properties} classpath resource,
 * whose {@code ${project.version}} placeholder is substituted at build time via
 * Maven resource filtering. This avoids the version drift that occurs when the
 * version has to be maintained in more than one place.
 */
public final class VersionInfo {

    /** Placeholder returned when the version cannot be resolved at runtime. */
    public static final String UNKNOWN_VERSION = "unknown";

    /** Prefix identifying this caching client in the SDK UserAgent header. */
    public static final String USER_AGENT_PREFIX = "AwsSecretCache/";

    /** Name of the filtered classpath resource holding the version. */
    static final String VERSION_RESOURCE = "version.properties";

    /** Properties key under which the version is stored. */
    static final String VERSION_KEY = "version";

    /**
     * Library version number, resolved at runtime from {@code pom.xml} via the
     * filtered {@code version.properties} resource. Falls back to
     * {@link #UNKNOWN_VERSION} when the resource is unavailable.
     */
    public static final String RELEASE_VERSION = resolveVersion();

    /**
     * User agent for AWS Secrets Manager API calls.
     */
    public static final String USER_AGENT = USER_AGENT_PREFIX + RELEASE_VERSION;

    private VersionInfo() {
    }

    /**
     * Resolves the library version from the filtered {@code version.properties}
     * classpath resource. Never throws; returns {@link #UNKNOWN_VERSION} if the
     * resource is missing or unreadable, so that a failure to resolve the version
     * can never break secret retrieval.
     *
     * @return the resolved version, or {@link #UNKNOWN_VERSION}.
     */
    private static String resolveVersion() {
        return readVersion(VersionInfo.class.getResourceAsStream(VERSION_RESOURCE));
    }

    /**
     * Parses the {@code version} property from the given stream, closing the
     * stream before returning.
     *
     * @param in the properties stream (may be {@code null}); this method takes
     *           ownership and closes it.
     * @return the version value, or {@link #UNKNOWN_VERSION} when the stream is
     *         {@code null}, unreadable, missing the key, blank, or still contains
     *         an unsubstituted Maven placeholder.
     */
    public static String readVersion(final InputStream in) {
        if (in == null) {
            return UNKNOWN_VERSION;
        }
        try (InputStream stream = in) {
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty(VERSION_KEY);
            if (version == null) {
                return UNKNOWN_VERSION;
            }
            version = version.trim();
            if (version.isEmpty() || version.contains("${")) {
                return UNKNOWN_VERSION;
            }
            return version;
        } catch (IOException e) {
            return UNKNOWN_VERSION;
        }
    }

    /**
     * Builds the UserAgent suffix for the caching client, preserving any suffix a
     * caller has already configured. When a caller suffix is present, the caching
     * identifier is appended to it ({@code "<caller> AwsSecretCache/<version>"});
     * otherwise the caching identifier is returned on its own.
     *
     * @param callerSuffix the caller-provided UserAgent suffix (may be {@code null}
     *                     or blank).
     * @return the combined UserAgent suffix.
     */
    public static String userAgentSuffix(final String callerSuffix) {
        if (callerSuffix == null || callerSuffix.trim().isEmpty()) {
            return USER_AGENT;
        }
        return callerSuffix + " " + USER_AGENT;
    }
}
