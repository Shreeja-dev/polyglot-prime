package org.techbd.ingest.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for UUIDv7Generator using FasterXML library.
 * 
 * Tests cover:
 * - Basic generation and validation
 * - Timestamp extraction and accuracy
 * - Time-ordering properties
 * - Thread safety and concurrent generation
 * - Uniqueness guarantees
 * - Error handling
 * - Performance characteristics
 */
public class UUIDv7GeneratorTest {
    
    /**
     * Tests basic UUID v7 generation.
     * Verifies that generated UUIDs are valid version 7 UUIDs with correct variant.
     */
    @Test
    @DisplayName("Should generate valid UUIDv7")
    void testGenerateBasic() {
        UUID uuid = UUIDv7Generator.generate();
        
        assertNotNull(uuid, "Generated UUID should not be null");
        assertEquals(7, uuid.version(), "UUID should be version 7");
        assertEquals(2, uuid.variant(), "UUID should be RFC 4122 variant (2)");
    }
    
    /**
     * Tests that generated UUIDs contain timestamps close to current time.
     * Verifies timestamp extraction accuracy within a reasonable tolerance.
     */
    @Test
    @DisplayName("Should embed current timestamp")
    void testTimestampEmbedding() {
        long beforeMs = System.currentTimeMillis();
        UUID uuid = UUIDv7Generator.generate();
        long afterMs = System.currentTimeMillis();
        
        long extractedMs = UUIDv7Generator.extractTimestamp(uuid);
        
        assertTrue(extractedMs >= beforeMs, 
            String.format("Extracted timestamp (%d) should be >= before timestamp (%d)", 
                extractedMs, beforeMs));
        assertTrue(extractedMs <= afterMs, 
            String.format("Extracted timestamp (%d) should be <= after timestamp (%d)", 
                extractedMs, afterMs));
    }
   
    
    /**
     * Tests version checking functionality.
     */
    @Test
    @DisplayName("Should correctly identify UUIDv7")
    void testIsVersion7() {
        UUID v7 = UUIDv7Generator.generate();
        assertTrue(UUIDv7Generator.isVersion7(v7), 
            "Generated UUID should be identified as version 7");
        
        UUID v4 = UUID.randomUUID();
        assertFalse(UUIDv7Generator.isVersion7(v4), 
            "Random UUID (v4) should not be identified as version 7");
    }
    
    /**
     * Tests that UUIDs are time-ordered when generated sequentially.
     */
    @Test
    @DisplayName("Should generate time-ordered UUIDs")
    void testTimeOrdering() throws InterruptedException {
        UUID uuid1 = UUIDv7Generator.generate();
        
        // Wait to ensure different timestamps
        Thread.sleep(2);
        
        UUID uuid2 = UUIDv7Generator.generate();
        
        assertTrue(uuid2.compareTo(uuid1) > 0, 
            "Later UUID should be lexicographically greater than earlier UUID");
        
        long ts1 = UUIDv7Generator.extractTimestamp(uuid1);
        long ts2 = UUIDv7Generator.extractTimestamp(uuid2);
        
        assertTrue(ts2 >= ts1, 
            String.format("Later timestamp (%d) should be >= earlier timestamp (%d)", ts2, ts1));
    }
    
    /**
     * Tests uniqueness of generated UUIDs.
     * Generates 10,000 UUIDs and verifies no duplicates.
     */
    @Test
    @DisplayName("Should generate unique UUIDs")
    void testUniqueness() {
        int count = 10_000;
        Set<UUID> uuids = new HashSet<>(count);
        
        for (int i = 0; i < count; i++) {
            UUID uuid = UUIDv7Generator.generate();
            assertTrue(uuids.add(uuid), 
                "UUID should be unique: " + uuid);
        }
        
        assertEquals(count, uuids.size(), 
            String.format("Should generate exactly %d unique UUIDs", count));
    }
    
    /**
     * Tests thread safety with concurrent UUID generation.
     * Multiple threads generate UUIDs simultaneously to verify no duplicates.
     */
    @Test
    @DisplayName("Should be thread-safe")
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int uuidsPerThread = 1000;
        
        Set<UUID> allUuids = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < uuidsPerThread; j++) {
                        UUID uuid = UUIDv7Generator.generate();
                        allUuids.add(uuid);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), 
            "All threads should complete within timeout");
        executor.shutdown();
        
        int expectedCount = threadCount * uuidsPerThread;
        assertEquals(expectedCount, allUuids.size(), 
            String.format("Should generate %d unique UUIDs across all threads", expectedCount));
    }
    
    /**
     * Tests batch generation functionality.
     */
    @Test
    @DisplayName("Should generate batch of UUIDs")
    void testGenerateBatch() {
        int count = 100;
        UUID[] uuids = UUIDv7Generator.generateBatch(count);
        
        assertNotNull(uuids, "Batch should not be null");
        assertEquals(count, uuids.length, "Batch should contain requested count");
        
        // Verify all are version 7 and unique
        Set<UUID> uniqueUuids = new HashSet<>();
        for (UUID uuid : uuids) {
            assertNotNull(uuid, "Each UUID should not be null");
            assertTrue(UUIDv7Generator.isVersion7(uuid), "Each UUID should be version 7");
            assertTrue(uniqueUuids.add(uuid), "Each UUID should be unique");
        }
    }
    
    /**
     * Tests batch generation with edge cases.
     */
    @Test
    @DisplayName("Should handle batch edge cases")
    void testGenerateBatchEdgeCases() {
        // Empty batch
        UUID[] empty = UUIDv7Generator.generateBatch(0);
        assertNotNull(empty);
        assertEquals(0, empty.length);
        
        // Single UUID
        UUID[] single = UUIDv7Generator.generateBatch(1);
        assertNotNull(single);
        assertEquals(1, single.length);
        
        // Negative count should throw exception
        assertThrows(IllegalArgumentException.class, 
            () -> UUIDv7Generator.generateBatch(-1),
            "Should throw exception for negative count");
    }
    
    /**
     * Tests string formatting with timestamp.
     */
    @Test
    @DisplayName("Should format UUID with timestamp")
    void testToStringWithTimestamp() {
        UUID uuid = UUIDv7Generator.generate();
        String formatted = UUIDv7Generator.toStringWithTimestamp(uuid);
        
        assertNotNull(formatted, "Formatted string should not be null");
        assertTrue(formatted.contains(uuid.toString()), 
            "Should contain UUID string");
        assertTrue(formatted.contains("timestamp"), 
            "Should mention 'timestamp'");
        assertTrue(formatted.contains("ms"), 
            "Should include millisecond notation");
    }
   
    /**
     * Tests error handling for null UUID in isVersion7.
     */
    @Test
    @DisplayName("Should throw NullPointerException for null UUID in isVersion7")
    void testIsVersion7Null() {
        assertThrows(NullPointerException.class, 
            () -> UUIDv7Generator.isVersion7(null),
            "Should throw NullPointerException for null UUID");
    }
    
    /**
     * Tests error handling for null UUID in extractTimestamp.
     */
    @Test
    @DisplayName("Should throw NullPointerException for null UUID in extractTimestamp")
    void testExtractTimestampNull() {
        assertThrows(NullPointerException.class, 
            () -> UUIDv7Generator.extractTimestamp(null),
            "Should throw NullPointerException for null UUID");
    }
    
    /**
     * Tests error handling for non-v7 UUID in extractTimestamp.
     */
    @Test
    @DisplayName("Should throw IllegalArgumentException for non-v7 UUID in extractTimestamp")
    void testExtractTimestampInvalidVersion() {
        UUID v4 = UUID.randomUUID();
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> UUIDv7Generator.extractTimestamp(v4),
            "Should throw exception for non-v7 UUID"
        );
        
        assertTrue(exception.getMessage().contains("not version 7"), 
            "Exception message should mention version mismatch");
    }
    
    /**
     * Tests error handling for non-v7 UUID in extractInstant.
     */
    @Test
    @DisplayName("Should throw IllegalArgumentException for non-v7 UUID in extractInstant")
    void testExtractInstantInvalidVersion() {
        UUID v4 = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class,
            () -> UUIDv7Generator.extractInstant(v4),
            "Should throw exception for non-v7 UUID");
    }
       
    /**
     * Tests UUID format compliance.
     */
    @Test
    @DisplayName("Should generate RFC 4122 compliant UUIDs")
    void testFormatCompliance() {
        UUID uuid = UUIDv7Generator.generate();
        String uuidString = uuid.toString();
        
        // Standard UUID format: 8-4-4-4-12 (36 characters with dashes)
        assertEquals(36, uuidString.length(), "UUID string should be 36 characters");
        
        // Verify dash positions
        assertEquals('-', uuidString.charAt(8), "Dash at position 8");
        assertEquals('-', uuidString.charAt(13), "Dash at position 13");
        assertEquals('-', uuidString.charAt(18), "Dash at position 18");
        assertEquals('-', uuidString.charAt(23), "Dash at position 23");
        
        // Verify version field (character 14 should be '7')
        assertEquals('7', uuidString.charAt(14), "Version should be 7 at position 14");
        
        // Verify variant field (character 19 should be 8, 9, a, or b for RFC 4122)
        char variantChar = Character.toLowerCase(uuidString.charAt(19));
        assertTrue("89ab".indexOf(variantChar) >= 0, 
            "Variant should be RFC 4122 compliant (8, 9, a, or b), but was: " + variantChar);
    }
    
    /**
     * Tests that generator can be accessed for advanced usage.
     */
    @Test
    @DisplayName("Should provide access to underlying generator")
    void testGetGenerator() {
        assertNotNull(UUIDv7Generator.getGenerator(), 
            "Generator instance should not be null");
    }
        
    /**
     * Tests natural ordering/sortability of UUIDv7s.
     */
    @Test
    @DisplayName("Should be sortable by creation time")
    void testSortability() throws InterruptedException {
        UUID[] uuids = new UUID[10];
        
        for (int i = 0; i < uuids.length; i++) {
            uuids[i] = UUIDv7Generator.generate();
            if (i < uuids.length - 1) {
                Thread.sleep(1); // Ensure different timestamps
            }
        }
        
        // Verify they're in chronological order
        for (int i = 1; i < uuids.length; i++) {
            assertTrue(uuids[i].compareTo(uuids[i - 1]) > 0, 
                String.format("UUID at index %d should be greater than UUID at index %d", 
                    i, i - 1));
        }
    }
}