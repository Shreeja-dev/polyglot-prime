package org.techbd.ingest.config;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * UUID Version 7 Generator using FasterXML java-uuid-generator library.
 * 
 * UUIDv7 is a time-ordered UUID that embeds a Unix timestamp in milliseconds,
 * providing better database performance and natural sorting compared to random UUIDs.
 * 
 * Maven Dependency:
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>com.fasterxml.uuid</groupId>
 *     <artifactId>java-uuid-generator</artifactId>
 *     <version>5.1.1</version>
 * </dependency>
 * }
 * </pre>
 * 
 * Usage Examples:
 * <pre>
 * // Generate a new UUIDv7
 * UUID uuid = UUIDv7Generator.generate();
 * 
 * // Extract timestamp from UUIDv7
 * long timestamp = UUIDv7Generator.extractTimestamp(uuid);
 * Instant instant = UUIDv7Generator.extractInstant(uuid);
 * 
 * // Check if UUID is version 7
 * boolean isV7 = UUIDv7Generator.isVersion7(uuid);
 * </pre>
 * 
 * Benefits:
 * - Time-ordered: Better for database indexes (B-tree)
 * - Reduced fragmentation: Sequential IDs improve insert performance
 * - Sortable: Natural chronological ordering
 * - Compatible: Standard UUID format
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-uuidrev-rfc4122bis">UUID RFC Draft</a>
 */
public class UUIDv7Generator {
    
    /**
     * Thread-safe UUID v7 generator instance.
     * TimeBasedEpochGenerator generates UUIDs with Unix epoch timestamps (version 7).
     */
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private UUIDv7Generator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Generate a new UUIDv7 with the current timestamp.
     * 
     * The UUID format is:
     * - 48 bits: Unix timestamp in milliseconds
     * - 4 bits: Version (0111 = 7)
     * - 12 bits: Random data
     * - 2 bits: Variant (10)
     * - 62 bits: Random data
     * 
     * Example output: 018c5f88-9a3e-7abc-9def-123456789abc
     * 
     * @return A new time-ordered UUIDv7
     */
    public static UUID generate() {
        return GENERATOR.generate();
    }
    
    /**
     * Check if a UUID is version 7.
     * 
     * @param uuid The UUID to check
     * @return true if the UUID is version 7, false otherwise
     * @throws NullPointerException if uuid is null
     */
    public static boolean isVersion7(UUID uuid) {
        if (uuid == null) {
            throw new NullPointerException("UUID cannot be null");
        }
        return uuid.version() == 7;
    }
    
    /**
     * Extract the Unix timestamp (in milliseconds) from a UUIDv7.
     * 
     * The timestamp is embedded in the first 48 bits of the UUID.
     * 
     * @param uuid The UUIDv7 to extract timestamp from
     * @return Unix timestamp in milliseconds
     * @throws IllegalArgumentException if the UUID is not version 7
     * @throws NullPointerException if uuid is null
     */
    public static long extractTimestamp(UUID uuid) {
        if (uuid == null) {
            throw new NullPointerException("UUID cannot be null");
        }
        
        if (!isVersion7(uuid)) {
            throw new IllegalArgumentException(
                "UUID is not version 7. Actual version: " + uuid.version());
        }
        
        // Extract the 48-bit timestamp from the most significant bits
        long msb = uuid.getMostSignificantBits();
        return (msb >>> 16) & 0xFFFF_FFFF_FFFFL;
    }
    
    /**
     * Extract the Instant from a UUIDv7.
     * 
     * Converts the embedded timestamp to a java.time.Instant.
     * 
     * @param uuid The UUIDv7 to extract timestamp from
     * @return Instant representing the timestamp
     * @throws IllegalArgumentException if the UUID is not version 7
     * @throws NullPointerException if uuid is null
     */
    public static Instant extractInstant(UUID uuid) {
        long timestamp = extractTimestamp(uuid);
        return Instant.ofEpochMilli(timestamp);
    }
    /**
     * Format a UUIDv7 with timestamp information for debugging.
     * 
     * Example output:
     * "018c5f88-9a3e-7abc-9def-123456789abc (timestamp: 2024-11-27T10:30:00.123Z / 1732704600123 ms)"
     * 
     * @param uuid The UUID to format
     * @return String representation with timestamp information
     * @throws NullPointerException if uuid is null
     */
    public static String toStringWithTimestamp(UUID uuid) {
        if (uuid == null) {
            throw new NullPointerException("UUID cannot be null");
        }
        
        if (!isVersion7(uuid)) {
            return uuid.toString() + " (not UUIDv7 - version " + uuid.version() + ")";
        }
        
        Instant instant = extractInstant(uuid);
        long timestamp = extractTimestamp(uuid);
        
        return String.format("%s (timestamp: %s / %d ms)", 
            uuid.toString(), 
            instant.toString(), 
            timestamp);
    }
    
    /**
     * Generate multiple UUIDv7s.
     * Useful for batch operations.
     * 
     * @param count Number of UUIDs to generate
     * @return Array of UUIDv7s
     * @throws IllegalArgumentException if count is negative
     */
    public static UUID[] generateBatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        
        UUID[] uuids = new UUID[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = generate();
        }
        return uuids;
    }
    
    /**
     * Get the generator instance for advanced usage.
     * 
     * This allows direct access to the underlying FasterXML generator
     * for custom configurations or advanced features.
     * 
     * @return The TimeBasedEpochGenerator instance
     */
    public static TimeBasedEpochGenerator getGenerator() {
        return GENERATOR;
    }
}
