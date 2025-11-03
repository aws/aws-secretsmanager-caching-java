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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * SecretCacheTest.
 */
public class SecretCacheTest {

    @Mock
    private SecretsManagerClient asm;

    @Mock
    private DescribeSecretResponse describeSecretResponse;

    private GetSecretValueResponse getSecretValueResponse = GetSecretValueResponse.builder()
            .versionStages(Arrays.asList("v1")).build();

    @Mock
    private SecretCacheConfiguration secretCacheConfiguration;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static void repeat(int number, IntConsumer c) {
        for (int n = 0; n < number; ++n) {
            c.accept(n);
        }
    }

    @Test
    public void secretCacheConstructorTest() {
        // coverage for null parameters to constructor
        SecretCache sc1 = null;
        SecretCache sc2 = null;
        try {
            sc1 = new SecretCache((SecretCacheConfiguration) null);
            sc1.close();
        } catch (Exception e) {
        }
        try {
            sc2 = new SecretCache((SecretsManagerClientBuilder) null);
            sc2.close();
        } catch (Exception e) {
        }
    }

    @Test
    public void secretCacheConstructorTestDefault() {
        try (MockedStatic<SecretsManagerClient> mockSmc = Mockito.mockStatic(SecretsManagerClient.class)) {
            SecretsManagerClientBuilder mock = Mockito.mock(SecretsManagerClientBuilder.class);
            mockSmc.when(SecretsManagerClient::builder).thenReturn(mock);
            ClientOverrideConfiguration overrideConfiguration = Mockito.mock(ClientOverrideConfiguration.class);
            ClientOverrideConfiguration.Builder builder = Mockito.mock(ClientOverrideConfiguration.Builder.class);
            when(overrideConfiguration.toBuilder()).thenReturn(builder);
            when(builder.putAdvancedOption(any(), anyString())).thenReturn(builder);
            when(builder.build()).thenReturn(overrideConfiguration);
            when(mock.overrideConfiguration(Mockito.any(ClientOverrideConfiguration.class))).thenReturn(mock);
            when(mock.overrideConfiguration()).thenReturn(overrideConfiguration);
            when(mock.build()).thenReturn(asm);
            SecretCache sc1 = new SecretCache();
            sc1.close();

            verify(builder).putAdvancedOption(eq(SdkAdvancedClientOption.USER_AGENT_SUFFIX), anyString());
        }
    }

    @Test
    public void secretCacheConstructorTestCustomClient() {
        SecretsManagerClientBuilder mock = Mockito.mock(SecretsManagerClientBuilder.class);
        ClientOverrideConfiguration overrideConfiguration = Mockito.mock(ClientOverrideConfiguration.class);
        ClientOverrideConfiguration.Builder builder = Mockito.mock(ClientOverrideConfiguration.Builder.class);
        when(overrideConfiguration.toBuilder()).thenReturn(builder);
        when(builder.putAdvancedOption(any(), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(overrideConfiguration);
        when(mock.overrideConfiguration(Mockito.any(ClientOverrideConfiguration.class))).thenReturn(mock);
        when(mock.overrideConfiguration()).thenReturn(overrideConfiguration);
        when(mock.build()).thenReturn(asm);

        SecretCache sc = new SecretCache(mock);
        sc.close();

        verify(builder).putAdvancedOption(eq(SdkAdvancedClientOption.USER_AGENT_SUFFIX), anyString());
    }

    @Deprecated
    @Test
    public void testForceRefreshJitterConfiguration() {
        // Test default value
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        Assert.assertEquals(config.getForceRefreshJitterMillis(),
                SecretCacheConfiguration.DEFAULT_FORCE_REFRESH_JITTER);

        // Test setting a custom value
        long customJitter = 250L;
        config.setForceRefreshJitterMillis(customJitter);
        Assert.assertEquals(config.getForceRefreshJitterMillis(), customJitter);

        // Test zero is valid
        config.setForceRefreshJitterMillis(0);
        Assert.assertEquals(config.getForceRefreshJitterMillis(), 0);
    }

    @Test
    public void testForceRefreshJitterDurationConfiguration() {
        // Test default value
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        Assert.assertEquals(config.getForceRefreshJitter(),
                SecretCacheConfiguration.DEFAULT_FORCE_REFRESH_JITTER_DURATION);

        // Test setting a custom value
        Duration customJitter = Duration.ofMillis(250);
        config.setForceRefreshJitter(customJitter);
        Assert.assertEquals(config.getForceRefreshJitter(), customJitter);

        // Test zero is valid
        config.setForceRefreshJitter(Duration.ZERO);
        Assert.assertEquals(config.getForceRefreshJitter(), Duration.ZERO);
    }

    @Deprecated
    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Force refresh jitter must be greater than or equal to zero")
    public void testForceRefreshJitterValidation() {
        // Test that negative values throw an exception
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        config.setForceRefreshJitterMillis(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Force refresh jitter must be greater than or equal to zero")
    public void testForceRefreshJitterDurationValidation() {
        // Test that negative values throw an exception
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        config.setForceRefreshJitter(Duration.ofMillis(-1));
    }

    @Test
    public void basicSecretCacheTest() {
        final String secret = "basicSecretCacheTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);
        GetSecretValueResponse.Builder resBuilder = GetSecretValueResponse.builder().secretString(secret)
                .secretBinary(SdkBytes.fromByteArray(secret.getBytes()));
        getSecretValueResponse = resBuilder.build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);

        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        repeat(10, n -> Assert.assertEquals(sc.getSecretBinary(""),
                ByteBuffer.wrap(secret.getBytes())));
        sc.close();
    }

    @Test
    public void hookSecretCacheTest() {
        final String secret = "hookSecretCacheTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        GetSecretValueResponse.Builder resBuilder = GetSecretValueResponse.builder().secretString(secret)
                .secretBinary(SdkBytes.fromByteArray(secret.getBytes()));
        getSecretValueResponse = resBuilder.build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        class Hook implements SecretCacheHook {
            private HashMap<Integer, Object> map = new HashMap<Integer, Object>();

            public Object put(final Object o) {
                Integer key = map.size();
                map.put(key, o);
                return key;
            }

            public Object get(final Object o) {
                return map.get((Integer) o);
            }

            public int getCount() {
                return map.size();
            }
        }
        Hook hook = new Hook();
        SecretCache sc = new SecretCache(new SecretCacheConfiguration()
                .withClient(asm)
                .withCacheHook(hook));

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        repeat(10, n -> Assert.assertEquals(sc.getSecretBinary(""),
                ByteBuffer.wrap(secret.getBytes())));
        Assert.assertEquals(hook.getCount(), 2);
        sc.close();
    }

    @Test
    public void secretCacheNullStagesTest() {
        final String secret = "basicSecretCacheTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);
        GetSecretValueResponse.Builder resBuilder = GetSecretValueResponse.builder().secretString(secret)
                .secretBinary(SdkBytes.fromByteArray(secret.getBytes())).versionStages((Collection<String>) null);
        getSecretValueResponse = resBuilder.build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        repeat(10, n -> Assert.assertEquals(sc.getSecretBinary(""),
                ByteBuffer.wrap(secret.getBytes())));
        sc.close();
    }

    @Test
    public void basicSecretCacheRefreshNowTest() throws Throwable {
        final String secret = "basicSecretCacheRefreshNowTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        GetSecretValueResponse.Builder resBuilder = GetSecretValueResponse.builder().secretString(secret)
                .secretBinary(SdkBytes.fromByteArray(secret.getBytes()));
        getSecretValueResponse = resBuilder.build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        sc.refreshNow("");
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheByteBufferTest() {
        final String secret = "basicSecretCacheByteBufferTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        ByteBuffer buffer = ByteBuffer.allocate(secret.getBytes(StandardCharsets.UTF_8).length);

        buffer.put(secret.getBytes(StandardCharsets.UTF_8)).rewind();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        GetSecretValueResponse diff = GetSecretValueResponse.builder().secretBinary(SdkBytes.fromByteBuffer(buffer))
                .build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(diff);

        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(StandardCharsets.UTF_8.decode(sc.getSecretBinary("")).toString(), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        Assert.assertEquals(sc.getSecretBinary(""), ByteBuffer.wrap(secret.getBytes()));
        sc.close();
    }

    @Test
    public void basicSecretCacheMultipleTest() {
        final String secretA = "basicSecretCacheMultipleTestA";
        final String secretB = "basicSecretCacheMultipleTestB";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);
        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secretA).build();

        GetSecretValueResponse res2 = GetSecretValueResponse.builder().secretString(secretB).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);

        Mockito.when(asm.getSecretValue(ArgumentMatchers.argThat(new ArgumentMatcher<GetSecretValueRequest>() {
            @Override
            public boolean matches(GetSecretValueRequest argument) {
                if (argument == null) {
                    return false;
                }
                return argument.secretId().equals("SecretA");
            }
        }))).thenReturn(getSecretValueResponse);

        Mockito.when(asm.getSecretValue(ArgumentMatchers.argThat(new ArgumentMatcher<GetSecretValueRequest>() {
            @Override
            public boolean matches(GetSecretValueRequest argument) {
                if (argument == null) {
                    return false;
                }
                return argument.secretId().equals("SecretB");
            }
        }))).thenReturn(res2);

        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString("SecretA"), secretA));
        repeat(10, n -> Assert.assertEquals(sc.getSecretString("SecretB"), secretB));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(2)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheRefreshTest() throws Throwable {
        final String secret = "basicSecretCacheRefreshTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);
        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secret).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);

        SecretCache sc = new SecretCache(new SecretCacheConfiguration()
                .withClient(asm)
                .withCacheItemTTL(Duration.ofMillis(500)));

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        // Wait long enough to expire the TTL on the cached item.
        Thread.sleep(600);
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), secret));
        // Verify that the refresh occurred after the ttl
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheRefreshNullVersionIdsToStagesReturnsNull() throws Throwable {
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(null);
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);

        SecretCache sc = new SecretCache(new SecretCacheConfiguration()
                .withClient(asm));

        Assert.assertNull(sc.getSecretString(""));

        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        sc.close();
    }

    @Deprecated
    @Test
    public void secretCacheRefreshAfterVersionChangeTestDeprecated() throws Throwable {
        // Kept around to cover .withCacheItemTTL(long cacheItemTTL)
        final String secret = "secretCacheRefreshAfterVersionChangeTestDeprecated";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secret).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(new SecretCacheConfiguration()
                .withClient(asm)
                .withForceRefreshJitterMillis(1)
                .withCacheItemTTL(500));

        // Request the secret multiple times and verify the correct result
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        // Wait long enough to expire the TTL on the cached item.
        Thread.sleep(502);
        versionMap.clear();
        // Simulate a change in secret version values
        versionMap.put("versionIdNew", Arrays.asList("AWSCURRENT"));
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));
        // Verify that the refresh occurred after the ttl
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(2)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test
    public void secretCacheRefreshAfterVersionChangeTest() throws Throwable {
        final String secret = "secretCacheRefreshAfterVersionChangeTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", Arrays.asList("AWSCURRENT"));
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secret).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(new SecretCacheConfiguration()
                .withClient(asm)
                .withMaxCacheSize(10)
                .withVersionStage("AWSCURRENT")
                .withForceRefreshJitter(Duration.ofMillis(1))
                .withCacheItemTTL(Duration.ofMillis(500)));

        // Request the secret multiple times and verify the correct result
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        // Wait long enough to expire the TTL on the cached item.
        Thread.sleep(502);
        versionMap.clear();
        // Simulate a change in secret version values
        versionMap.put("versionIdNew", Arrays.asList("AWSCURRENT"));
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));
        // Verify that the refresh occurred after the ttl
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(2)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheTestNoVersions() {
        final String secret = "basicSecretCacheTestNoVersion";

        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secret).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, m -> Assert.assertNull(sc.getSecretString("")));
        repeat(10, m -> Assert.assertNull(sc.getSecretBinary("")));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(0)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

    @Test(expectedExceptions = { RuntimeException.class })
    public void basicSecretCacheExceptionTest() {
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenThrow(new RuntimeException());
        SecretCache sc = new SecretCache(asm);
        sc.getSecretString("");
        sc.close();
    }

    @Test
    public void basicSecretCacheExceptionRefreshNowTest() throws Throwable {
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenThrow(new RuntimeException());
        SecretCache sc = new SecretCache(asm);
        Assert.assertFalse(sc.refreshNow(""));
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Assert.assertFalse(sc.refreshNow(""));
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheExceptionRetryTest() throws Throwable {
        final int retryCount = 10;
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenThrow(new RuntimeException());
        SecretCache sc = new SecretCache(asm);
        for (int n = 0; n < retryCount; ++n) {
            try {
                sc.getSecretString("");
                Assert.fail("Exception should have been thrown!");
            } catch (RuntimeException ex) {
            }
        }
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));

        // Wait the backoff interval before retrying failed requests to verify
        // a retry will be performed.
        Thread.sleep(2100);
        try {
            sc.getSecretString("");
            Assert.fail("Exception should have been thrown!");
        } catch (RuntimeException ex) {
        }
        // The api call should have been retried after the delay.
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        sc.close();
    }

    @Test
    public void basicSecretCacheNullTest() {
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(null);
        SecretCache sc = new SecretCache(asm);
        Assert.assertNull(sc.getSecretString(""));
        sc.close();
    }

    @Test
    public void basicSecretCacheNullStagesTest() {
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(null);
        SecretCache sc = new SecretCache(asm);
        Assert.assertNull(sc.getSecretString(""));
        sc.close();
    }

    @Test
    public void basicSecretCacheVersionWithNullStageTest() {
        final String secret = "basicSecretCacheTest";
        Map<String, List<String>> versionMap = new HashMap<String, List<String>>();
        versionMap.put("versionId", null);
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(versionMap);

        getSecretValueResponse = GetSecretValueResponse.builder().secretString(secret).build();

        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(asm.getSecretValue(Mockito.any(GetSecretValueRequest.class))).thenReturn(getSecretValueResponse);
        SecretCache sc = new SecretCache(asm);

        // Request the secret multiple times and verify the correct result
        repeat(10, n -> Assert.assertEquals(sc.getSecretString(""), null));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(0)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
        sc.close();
    }

}
