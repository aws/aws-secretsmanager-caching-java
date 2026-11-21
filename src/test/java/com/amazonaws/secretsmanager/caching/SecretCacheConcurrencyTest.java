/*
 * Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.amazonaws.secretsmanager.caching.cache.LRUCache;
import com.amazonaws.secretsmanager.caching.cache.SecretCacheItem;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class SecretCacheConcurrencyTest {
    private static final String SECRET_A = "secret-a";
    private static final String SECRET_B = "secret-b";

    private enum Operation { STRING, BINARY, REFRESH }
    private enum Interleaving { INSERT_THEN_EVICT, EXISTING_WINNER, WINNER_THEN_EVICT }

    @DataProvider
    public Object[][] cacheInterleavings() {
        Object[][] cases = new Object[9][2];
        int index = 0;
        for (Operation operation : Operation.values()) {
            for (Interleaving interleaving : Interleaving.values()) {
                cases[index++] = new Object[] {operation, interleaving};
            }
        }
        return cases;
    }

    @Test(dataProvider = "cacheInterleavings")
    public void requestSurvivesConcurrentInsertion(Operation operation, Interleaving interleaving) throws Exception {
        SecretsManagerClient client = Mockito.mock(SecretsManagerClient.class);
        Mockito.when(client.describeSecret(Mockito.any(DescribeSecretRequest.class)))
                .thenReturn(DescribeSecretResponse.builder().versionIdsToStages(
                        Collections.singletonMap("version", Collections.singletonList("AWSCURRENT"))).build());
        Mockito.when(client.getSecretValue(Mockito.any(GetSecretValueRequest.class)))
                .thenAnswer(invocation -> {
                    String id = ((GetSecretValueRequest) invocation.getArgument(0)).secretId();
                    return GetSecretValueResponse.builder().secretString(id)
                            .secretBinary(SdkBytes.fromUtf8String(id)).build();
                });
        SecretCacheConfiguration config = new SecretCacheConfiguration().withClient(client).withMaxCacheSize(1);
        config.setForceRefreshJitterMillis(0);
        PausingCache items = new PausingCache(interleaving);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SecretCache cache = new SecretCache(config)) {
            Field field = SecretCache.class.getDeclaredField("cache");
            field.setAccessible(true);
            field.set(cache, items);
            Future<Object> request = executor.submit(() -> {
                items.requestThread = Thread.currentThread();
                switch (operation) {
                    case STRING:
                        return cache.getSecretString(SECRET_A);
                    case BINARY:
                        return cache.getSecretBinary(SECRET_A);
                    default:
                        return cache.refreshNow(SECRET_A);
                }
            });

            boolean winner = interleaving != Interleaving.INSERT_THEN_EVICT;
            boolean evict = interleaving != Interleaving.EXISTING_WINNER;
            if (winner) {
                await(items.missed);
                Assert.assertEquals(cache.getSecretString(SECRET_A), SECRET_A);
                items.continueInsert.countDown();
            }
            await(items.inserted);
            Assert.assertEquals(items.insertedMapping, !winner);
            if (evict) {
                Assert.assertEquals(cache.getSecretString(SECRET_B), SECRET_B);
                Assert.assertFalse(items.containsKey(SECRET_A));
            }
            items.continueRead.countDown();

            Object expected = SECRET_A;
            if (operation == Operation.BINARY) {
                expected = ByteBuffer.wrap(SECRET_A.getBytes(StandardCharsets.UTF_8));
            } else if (operation == Operation.REFRESH) {
                expected = Boolean.TRUE;
            }
            Assert.assertEquals(request.get(10, TimeUnit.SECONDS), expected);
            Assert.assertEquals(items.containsKey(SECRET_A), !evict);
            if (evict) {
                Assert.assertEquals(cache.getSecretString(SECRET_B), SECRET_B);
                Mockito.verify(client, Mockito.times(1)).describeSecret(
                        DescribeSecretRequest.builder().secretId(SECRET_B).build());
                Mockito.verify(client, Mockito.times(1)).getSecretValue(
                        GetSecretValueRequest.builder().secretId(SECRET_B).versionId("version").build());
            }
            int descriptions = winner && (evict || operation == Operation.REFRESH) ? 2 : 1;
            int values = operation == Operation.REFRESH ? (winner ? 1 : 0) : (winner && evict ? 2 : 1);
            Mockito.verify(client, Mockito.times(descriptions)).describeSecret(
                    DescribeSecretRequest.builder().secretId(SECRET_A).build());
            Mockito.verify(client, Mockito.times(values)).getSecretValue(
                    GetSecretValueRequest.builder().secretId(SECRET_A).versionId("version").build());
        } finally {
            items.continueInsert.countDown();
            items.continueRead.countDown();
            executor.shutdownNow();
            Assert.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent cache operation did not reach its barrier");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating cache operations", ex);
        }
    }

    // Pause outside the real LRU's map lock so the second caller can insert and evict normally.
    private static class PausingCache extends LRUCache<String, SecretCacheItem> {
        private final CountDownLatch missed = new CountDownLatch(1);
        private final CountDownLatch continueInsert = new CountDownLatch(1);
        private final CountDownLatch inserted = new CountDownLatch(1);
        private final CountDownLatch continueRead = new CountDownLatch(1);
        private final Interleaving interleaving;
        private Thread requestThread;
        private boolean firstGet = true;
        private boolean insertedMapping;

        PausingCache(Interleaving interleaving) {
            super(1);
            this.interleaving = interleaving;
        }

        @Override
        public SecretCacheItem get(String key) {
            SecretCacheItem item = super.get(key);
            if (Thread.currentThread() == requestThread && SECRET_A.equals(key) && firstGet) {
                firstGet = false;
                if (interleaving != Interleaving.INSERT_THEN_EVICT) {
                    missed.countDown();
                    await(continueInsert);
                }
            }
            return item;
        }

        @Override
        public boolean putIfAbsent(String key, SecretCacheItem value) {
            boolean added = super.putIfAbsent(key, value);
            if (Thread.currentThread() == requestThread && SECRET_A.equals(key)) {
                insertedMapping = added;
                inserted.countDown();
                await(continueRead);
            }
            return added;
        }
    }
}
