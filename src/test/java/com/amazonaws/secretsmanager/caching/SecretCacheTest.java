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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.core.SdkBytes;
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
                .withCacheItemTTL(500));

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
                .withCacheItemTTL(500));

        // Request the secret multiple times and verify the correct result
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));

        // Verify that multiple requests did not call the API
        Mockito.verify(asm, Mockito.times(1)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(1)).getSecretValue(Mockito.any(GetSecretValueRequest.class));

        // Wait long enough to expire the TTL on the cached item.
        Thread.sleep(600);
        versionMap.clear();
        // Simulate a change in secret version values
        versionMap.put("versionIdNew", Arrays.asList("AWSCURRENT"));
        repeat(5, n -> Assert.assertEquals(sc.getSecretString(""), secret));
        // Verify that the refresh occurred after the ttl
        Mockito.verify(asm, Mockito.times(2)).describeSecret(Mockito.any(DescribeSecretRequest.class));
        Mockito.verify(asm, Mockito.times(2)).getSecretValue(Mockito.any(GetSecretValueRequest.class));
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
