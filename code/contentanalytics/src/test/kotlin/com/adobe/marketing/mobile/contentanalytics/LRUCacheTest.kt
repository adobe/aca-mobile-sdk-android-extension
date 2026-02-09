/*
 * Copyright 2026 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.contentanalytics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LRUCacheTest {
    
    private lateinit var cache: LRUCache<String, String>
    
    @Before
    fun setup() {
        cache = LRUCache(capacity = 3)
    }
    
    @Test
    fun `test basic set and get`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        
        assertEquals("value1", cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
        assertNull(cache.get("key3"))
    }
    
    @Test
    fun `test capacity enforcement - evicts LRU`() {
        // Fill cache to capacity
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        assertEquals(3, cache.count())
        
        // Add 4th item - should evict key1 (least recently used)
        cache.set("key4", "value4")
        
        assertEquals(3, cache.count())
        assertNull("key1 should be evicted", cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
        assertEquals("value3", cache.get("key3"))
        assertEquals("value4", cache.get("key4"))
    }
    
    @Test
    fun `test LRU ordering - get marks as recently used`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        // Access key1 to make it recently used
        cache.get("key1")
        
        // Add 4th item - should evict key2 (now LRU)
        cache.set("key4", "value4")
        
        assertEquals("value1", cache.get("key1"))
        assertNull("key2 should be evicted", cache.get("key2"))
        assertEquals("value3", cache.get("key3"))
        assertEquals("value4", cache.get("key4"))
    }
    
    @Test
    fun `test update existing key doesn't evict`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        // Update existing key
        cache.set("key2", "updated_value2")
        
        assertEquals(3, cache.count())
        assertEquals("updated_value2", cache.get("key2"))
    }
    
    @Test
    fun `test remove`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        
        cache.remove("key1")
        
        assertEquals(1, cache.count())
        assertNull(cache.get("key1"))
        assertEquals("value2", cache.get("key2"))
    }
    
    @Test
    fun `test removeAll`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        cache.removeAll()
        
        assertEquals(0, cache.count())
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNull(cache.get("key3"))
    }
    
    @Test
    fun `test keys`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        val keys = cache.keys()
        
        assertEquals(3, keys.size)
        assertTrue(keys.contains("key1"))
        assertTrue(keys.contains("key2"))
        assertTrue(keys.contains("key3"))
    }
    
    @Test
    fun `test values`() {
        cache.set("key1", "value1")
        cache.set("key2", "value2")
        cache.set("key3", "value3")
        
        val values = cache.values()
        
        assertEquals(3, values.size)
        assertTrue(values.contains("value1"))
        assertTrue(values.contains("value2"))
        assertTrue(values.contains("value3"))
    }
    
    @Test
    fun `test thread safety - concurrent reads and writes`() {
        val largeCache = LRUCache<Int, String>(100)
        val latch = CountDownLatch(10)
        
        // Create 10 threads that concurrently read and write
        repeat(10) { threadNum ->
            thread {
                try {
                    repeat(100) { i ->
                        val key = threadNum * 100 + i
                        largeCache.set(key, "value_$key")
                        largeCache.get(key)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        
        // Wait for all threads to complete
        assertTrue("Threads should complete within timeout", latch.await(5, TimeUnit.SECONDS))
        
        // Cache should have exactly 100 items (capacity limit)
        assertEquals(100, largeCache.count())
    }
    
    @Test
    fun `test capacity of 1 works correctly`() {
        val singleCache = LRUCache<String, String>(1)
        
        singleCache.set("key1", "value1")
        assertEquals("value1", singleCache.get("key1"))
        
        singleCache.set("key2", "value2")
        assertNull("key1 should be evicted", singleCache.get("key1"))
        assertEquals("value2", singleCache.get("key2"))
        assertEquals(1, singleCache.count())
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test capacity of 0 throws exception`() {
        LRUCache<String, String>(0)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test negative capacity throws exception`() {
        LRUCache<String, String>(-1)
    }
    
    @Test
    fun `test large capacity doesn't evict prematurely`() {
        val largeCache = LRUCache<String, String>(1000)
        
        // Add 500 items - well under capacity
        repeat(500) { i ->
            largeCache.set("key$i", "value$i")
        }
        
        assertEquals(500, largeCache.count())
        
        // Verify all items still accessible
        repeat(500) { i ->
            assertEquals("value$i", largeCache.get("key$i"))
        }
    }
}
