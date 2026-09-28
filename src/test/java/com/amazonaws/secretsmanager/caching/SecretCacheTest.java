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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.regions.Region;
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
    public void testForceRefreshJitterConfiguration() {
        // Test default value
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        Assert.assertEquals(config.getForceRefreshJitterMillis(), SecretCacheConfiguration.DEFAULT_FORCE_REFRESH_JITTER);
        
        // Test setting a custom value
        long customJitter = 250L;
        config.setForceRefreshJitterMillis(customJitter);
        Assert.assertEquals(config.getForceRefreshJitterMillis(), customJitter);
        
        // Test zero is valid
        config.setForceRefreshJitterMillis(0);
        Assert.assertEquals(config.getForceRefreshJitterMillis(), 0);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class, 
          expectedExceptionsMessageRegExp = "Force refresh jitter must be greater than or equal to zero")
    public void testForceRefreshJitterValidation() {
        // Test that negative values throw an exception
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        config.setForceRefreshJitterMillis(-1);
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

    @Test
    public void testPostQuantumTlsConfiguration() {
        // Test default is false
        SecretCacheConfiguration config = new SecretCacheConfiguration();
        Assert.assertFalse(config.isPostQuantumTlsEnabled());
        
        // Test setter
        config.setPostQuantumTlsEnabled(true);
        Assert.assertTrue(config.isPostQuantumTlsEnabled());
        
        // Test fluent method
        SecretCacheConfiguration config2 = new SecretCacheConfiguration()
            .withPostQuantumTlsEnabled(true);
        Assert.assertTrue(config2.isPostQuantumTlsEnabled());
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testSecretCacheWithPQTLSEnabled() {
        // Verify that providing both client and PQTLS flag throws exception
        SecretCacheConfiguration config = new SecretCacheConfiguration()
            .withClient(asm) 
            .withPostQuantumTlsEnabled(true);
        
        new SecretCache(config);  
    }

    @Test
    public void testSecretCacheWithPQTLSEnabledNoClient() {
        String originalRegion = System.getProperty("aws.region");
        try {
            System.setProperty("aws.region", "us-east-1");
            SecretCacheConfiguration config = new SecretCacheConfiguration()
                .withPostQuantumTlsEnabled(true);  // No .withClient() call
            SecretCache cache = new SecretCache(config);
            Assert.assertNotNull(cache);
            cache.close();
        } finally {
            if (originalRegion != null) {
                System.setProperty("aws.region", originalRegion);
            } else {
                System.clearProperty("aws.region");
            }
        }
    }

    /**
     * Opens a socket that is never accepted. It completes the TCP handshake but
     * never answers, like an endpoint that hangs after connecting.
     */
    private static ServerSocket hungEndpoint() throws Exception {
        return new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    }

    private static SecretsManagerClientBuilder hungEndpointClientBuilder(ServerSocket hung) {
        return SecretsManagerClient.builder()
                .endpointOverride(URI.create("http://127.0.0.1:" + hung.getLocalPort()))
                .region(Region.US_WEST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("akid", "secret")));
    }

    private static long millisToTimeOut(SecretCache cache) {
        long start = System.nanoTime();
        Assert.expectThrows(ApiCallAttemptTimeoutException.class, () -> cache.getSecretString("test"));
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    public void testDefaultApiCallAttemptTimeout() {
        Assert.assertEquals(SecretCacheConfiguration.DEFAULT_API_CALL_ATTEMPT_TIMEOUT, Duration.ofSeconds(2));
    }

    @Test
    public void testBuilderClientTimesOutOnHungEndpoint() throws Exception {
        try (ServerSocket hung = hungEndpoint();
             SecretCache cache = new SecretCache(hungEndpointClientBuilder(hung)
                     .overrideConfiguration(c -> c.retryStrategy(AwsRetryStrategy.doNotRetry())))) {
            long elapsed = millisToTimeOut(cache);
            Assert.assertTrue(elapsed >= 2000 && elapsed < 5000, "Took " + elapsed + " ms to give up");
        }
    }

    @Test
    public void testBuilderClientRetriesTimedOutAttempt() throws Exception {
        try (ServerSocket hung = hungEndpoint();
             SecretCache cache = new SecretCache(hungEndpointClientBuilder(hung)
                     .overrideConfiguration(c -> c.retryStrategy(
                             AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(2).build())))) {
            // Two 2 second attempts, plus a short backoff between them.
            long elapsed = millisToTimeOut(cache);
            Assert.assertTrue(elapsed >= 4000 && elapsed < 8000, "Took " + elapsed + " ms to give up");
        }
    }

    @Test
    public void testBuilderKeepsOwnAttemptTimeout() throws Exception {
        try (ServerSocket hung = hungEndpoint();
             SecretCache cache = new SecretCache(hungEndpointClientBuilder(hung)
                     .overrideConfiguration(c -> c.retryStrategy(AwsRetryStrategy.doNotRetry())
                             .apiCallAttemptTimeout(Duration.ofMillis(200))))) {
            long elapsed = millisToTimeOut(cache);
            Assert.assertTrue(elapsed < 2000,
                    "Took " + elapsed + " ms to give up, so the builder's own timeout was replaced");
        }
    }

    @Test
    public void testDefaultClientTimesOutOnHungEndpoint() throws Exception {
        String[] names = {"aws.region", "aws.endpointUrl", "aws.accessKeyId", "aws.secretAccessKey", "aws.maxAttempts"};
        Map<String, String> original = new HashMap<>();
        for (String name : names) {
            original.put(name, System.getProperty(name));
        }
        try (ServerSocket hung = hungEndpoint()) {
            System.setProperty("aws.region", "us-west-2");
            System.setProperty("aws.endpointUrl", "http://127.0.0.1:" + hung.getLocalPort());
            System.setProperty("aws.accessKeyId", "akid");
            System.setProperty("aws.secretAccessKey", "secret");
            System.setProperty("aws.maxAttempts", "1");
            try (SecretCache cache = new SecretCache(new SecretCacheConfiguration())) {
                long elapsed = millisToTimeOut(cache);
                Assert.assertTrue(elapsed >= 2000 && elapsed < 5000, "Took " + elapsed + " ms to give up");
            }
        } finally {
            for (String name : names) {
                if (original.get(name) != null) {
                    System.setProperty(name, original.get(name));
                } else {
                    System.clearProperty(name);
                }
            }
        }
    }

    private static List<Thread> threadsNamed(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Test
    public void testCachesShareOneTimeoutExecutor() throws Exception {
        // Each client would otherwise start its own SDK timer threads, which stay
        // alive because the cache doesn't close its client. That includes a builder
        // that sets its own timeout.
        int sdkThreadsBefore = threadsNamed("sdk-ScheduledExecutor").size();
        try (ServerSocket hung = hungEndpoint()) {
            for (int i = 0; i < 3; i++) {
                try (SecretCache cache = new SecretCache(hungEndpointClientBuilder(hung)
                        .overrideConfiguration(c -> c.retryStrategy(AwsRetryStrategy.doNotRetry())));
                     SecretCache ownTimeout = new SecretCache(hungEndpointClientBuilder(hung)
                        .overrideConfiguration(c -> c.retryStrategy(AwsRetryStrategy.doNotRetry())
                                .apiCallAttemptTimeout(Duration.ofMillis(200))))) {
                    millisToTimeOut(cache);
                    millisToTimeOut(ownTimeout);
                }
            }
        }
        Assert.assertEquals(threadsNamed("sdk-ScheduledExecutor").size(), sdkThreadsBefore);

        List<Thread> cacheThreads = threadsNamed("aws-secretsmanager-caching-timeout");
        Assert.assertEquals(cacheThreads.size(), 1);
        Assert.assertTrue(cacheThreads.get(0).isDaemon());
        Assert.assertSame(cacheThreads.get(0).getContextClassLoader(), SecretCache.class.getClassLoader());
    }

    @Test
    public void testCacheStillWorksAfterClose() {
        Mockito.when(asm.describeSecret(Mockito.any(DescribeSecretRequest.class))).thenReturn(describeSecretResponse);
        Mockito.when(describeSecretResponse.versionIdsToStages()).thenReturn(null);
        SecretCache cache = new SecretCache(asm);
        cache.close();
        Assert.assertNull(cache.getSecretString("test"));
        Mockito.verify(asm, Mockito.never()).close();
    }
}
