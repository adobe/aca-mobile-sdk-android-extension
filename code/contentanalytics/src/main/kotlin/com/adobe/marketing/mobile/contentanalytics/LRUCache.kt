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

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe LRU (Least Recently Used) cache with size limit
 * Automatically evicts least recently used items when capacity is reached
 * 
 * @param K Key type (must be hashable)
 * @param V Value type
 * @property capacity Maximum number of items to store
 */
internal class LRUCache<K, V>(private val capacity: Int) {
    
    init {
        require(capacity > 0) { "LRU cache capacity must be greater than 0" }
    }
    
    private class Node<K, V>(
        val key: K,
        var value: V,
        var prev: Node<K, V>? = null,
        var next: Node<K, V>? = null
    )
    
    private val lock = ReentrantReadWriteLock()
    private val cache = mutableMapOf<K, Node<K, V>>()
    private var head: Node<K, V>? = null
    private var tail: Node<K, V>? = null
    
    /**
     * Get value for key, marking it as recently used
     * @param key Key to look up
     * @return Value if exists, null otherwise
     * @note Uses write lock because moveToHead() mutates the linked list
     */
    fun get(key: K): V? = lock.write {
        val node = cache[key] ?: return null
        moveToHead(node)
        return node.value
    }
    
    /**
     * Set value for key, evicting LRU item if at capacity
     * @param key Key to store under
     * @param value Value to store
     */
    fun set(key: K, value: V) = lock.write {
        val existingNode = cache[key]
        
        if (existingNode != null) {
            // Update existing node
            existingNode.value = value
            moveToHead(existingNode)
        } else {
            // Add new node
            val newNode = Node(key, value)
            cache[key] = newNode
            addToHead(newNode)
            
            // Evict LRU if over capacity
            if (cache.size > capacity) {
                tail?.let { tailNode ->
                    remove(tailNode)
                    cache.remove(tailNode.key)
                }
            }
        }
    }
    
    /**
     * Remove value for key
     * @param key Key to remove
     */
    fun remove(key: K) = lock.write {
        val node = cache[key] ?: return@write
        remove(node)
        cache.remove(key)
    }
    
    /**
     * Remove all items from cache
     */
    fun removeAll() = lock.write {
        cache.clear()
        head = null
        tail = null
    }
    
    /**
     * Get current number of items in cache
     */
    fun count(): Int = lock.read {
        return cache.size
    }
    
    /**
     * Get all keys currently in cache
     */
    fun keys(): Set<K> = lock.read {
        return cache.keys.toSet()
    }
    
    /**
     * Get all values currently in cache
     */
    fun values(): List<V> = lock.read {
        return cache.values.map { it.value }
    }
    
    // MARK: - Private Helpers
    
    private fun moveToHead(node: Node<K, V>) {
        remove(node)
        addToHead(node)
    }
    
    private fun addToHead(node: Node<K, V>) {
        node.prev = null
        node.next = head
        head?.prev = node
        head = node
        
        if (tail == null) {
            tail = node
        }
    }
    
    private fun remove(node: Node<K, V>) {
        val prev = node.prev
        val next = node.next
        
        prev?.next = next
        next?.prev = prev
        
        if (node === head) {
            head = next
        }
        
        if (node === tail) {
            tail = prev
        }
        
        node.prev = null
        node.next = null
    }
}
