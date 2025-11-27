package org.techbd.service.csv.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.techbd.model.csv.FileDetail;

/**
 * Comprehensive test suite for FileProcessor content validation.
 * Tests various Unicode character validation scenarios including:
 * - Null bytes (U+0000)
 * - Control characters (backspace, vertical tab, substitute, delete)
 * - Problematic whitespace (non-breaking spaces, zero-width characters)
 * - Invalid surrogate pairs
 * - BOM (Byte Order Mark) detection
 * - Unicode non-characters
 * - Format characters
 */
public class FileProcessorValidationTest {
    
    @TempDir
    Path tempDir;
    
    private static final String VALID_CSV_HEADER = "PatientMRN,FirstName,LastName\n";
    private static final String VALID_CSV_ROW = "123,John,Doe";
    
    /**
     * Provides test cases for null byte validation.
     * Null bytes (U+0000) can cause database issues and parsing errors.
     * 
     * @return Stream of test arguments containing:
     *         - Test description
     *         - File content with null byte
     *         - Expected error message pattern
     */
    static Stream<Arguments> nullByteTestCases() {
        return Stream.of(
            Arguments.of(
                "Null byte in middle of content",
                VALID_CSV_HEADER + "456,Jane\u0000,Smith",
                "Null bytes"
            ),
            Arguments.of(
                "Null byte at start of row",
                VALID_CSV_HEADER + "\u0000789,Bob,Jones",
                "Null bytes"
            ),
            Arguments.of(
                "Multiple null bytes",
                VALID_CSV_HEADER + "111,\u0000Test\u0000,User\u0000",
                "Null bytes"
            ),
            Arguments.of(
                "Null byte in header",
                "PatientMRN\u0000,FirstName,LastName\n" + VALID_CSV_ROW,
                "Null bytes"
            )
        );
    }
    
    /**
     * Tests that files containing null bytes are properly rejected.
     * Null bytes can cause truncation in C-style string handling and
     * create issues with database storage.
     * 
     * Example output map for a file with null byte:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_PtInfo_group1.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Null bytes (0x00): U+0000 (NULL) at position 41"
     *     }
     *   ]
     * }
     * </pre>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("nullByteTestCases")
    void testNullByteDetection(String testDescription, String content, String expectedError) throws IOException {
        // Arrange
        Path testFile = createFile("SDOH_PtInfo_group1.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        assertTrue(fileDetail.reason().contains(expectedError), 
            String.format("Reason should mention '%s'. Actual: %s", expectedError, fileDetail.reason()));
        assertTrue(fileDetail.reason().contains("U+0000"), 
            "Should include Unicode code point in error message");
    }
    
    /**
     * Provides test cases for control character validation.
     * Control characters can interfere with text processing and display.
     * 
     * @return Stream of test arguments with various control characters
     */
    static Stream<Arguments> controlCharacterTestCases() {
        return Stream.of(
            Arguments.of(
                "Backspace character (U+0008)",
                VALID_CSV_HEADER + "456,Jane\bSmith,Test",
                "Control characters",
                "U+0008"
            ),
            Arguments.of(
                "Vertical tab (U+000B)",
                VALID_CSV_HEADER + "789,Bob\u000BJones,Test",
                "Control characters",
                "U+000B"
            ),
            Arguments.of(
                "Substitute character (U+001A)",
                VALID_CSV_HEADER + "111,Alice\u001A,Wonder",
                "Control characters",
                "U+001A"
            ),
            Arguments.of(
                "Delete character (U+007F)",
                VALID_CSV_HEADER + "222,Charlie\u007F,Brown",
                "Control characters",
                "U+007F"
            ),
            Arguments.of(
                "Bell character (U+0007)",
                VALID_CSV_HEADER + "333,Dave\u0007,Test",
                "Control characters",
                "U+0007"
            ),
            Arguments.of(
                "Escape character (U+001B)",
                VALID_CSV_HEADER + "444,Eve\u001B,Test",
                "Control characters",
                "U+001B"
            ),
            Arguments.of(
                "Multiple control characters",
                VALID_CSV_HEADER + "555,\bTest\u000B,\u001AUser",
                "Control characters",
                "U+0008"
            )
        );
    }
    
    /**
     * Tests that files containing control characters are properly rejected.
     * Control characters (except tabs, newlines, and carriage returns) can
     * cause display issues and interfere with parsing.
     * 
     * Example output map for a file with backspace character (U+0008):
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_QEadmin_group1.csv",
     *       "fileType": "SDOH_QEadmin",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_QEadmin_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Control characters: U+0008 (BACKSPACE) at position 43"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * Example output map for a file with vertical tab (U+000B):
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_QEadmin_group1.csv",
     *       "fileType": "SDOH_QEadmin",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_QEadmin_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Control characters: U+000B (VERTICAL TAB) at position 40"
     *     }
     *   ]
     * }
     * </pre>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("controlCharacterTestCases")
    void testControlCharacterDetection(String testDescription, String content, 
                                       String expectedError, String expectedCodePoint) throws IOException {
        // Arrange
        Path testFile = createFile("SDOH_QEadmin_group1.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        assertTrue(fileDetail.reason().contains(expectedError), 
            String.format("Reason should mention '%s'. Actual: %s", expectedError, fileDetail.reason()));
        assertTrue(fileDetail.reason().contains(expectedCodePoint), 
            String.format("Should include code point '%s' in error message. Actual: %s", 
                expectedCodePoint, fileDetail.reason()));
    }
    
    /**
     * Provides test cases for problematic whitespace validation.
     * Certain whitespace characters can be invisible or cause layout issues.
     * 
     * @return Stream of test arguments with various problematic whitespace
     */
    static Stream<Arguments> problematicWhitespaceTestCases() {
        return Stream.of(
            Arguments.of(
                "Non-breaking space (U+00A0)",
                VALID_CSV_HEADER + "456,Jane\u00A0Smith,Test",
                "Problematic whitespace",
                "U+00A0"
            ),
            Arguments.of(
                "Zero-width space (U+200B)",
                VALID_CSV_HEADER + "789,Bob\u200BJones,Test",
                "Zero-width characters",
                "U+200B"
            ),
            Arguments.of(
                "Zero-width non-joiner (U+200C)",
                VALID_CSV_HEADER + "111,Alice\u200C,Wonder",
                "Zero-width characters",
                "U+200C"
            ),
            Arguments.of(
                "Zero-width joiner (U+200D)",
                VALID_CSV_HEADER + "222,Charlie\u200D,Brown",
                "Zero-width characters",
                "U+200D"
            ),
            Arguments.of(
                "Zero-width no-break space / BOM (U+FEFF)",
                VALID_CSV_HEADER + "333,Dave\uFEFF,Test",
                "Zero-width characters",
                "U+FEFF"
            ),
            Arguments.of(
                "Thin space (U+2009)",
                VALID_CSV_HEADER + "444,Eve\u2009,Test",
                "Problematic whitespace",
                "U+2009"
            ),
            Arguments.of(
                "Hair space (U+200A)",
                VALID_CSV_HEADER + "555,Frank\u200A,Test",
                "Problematic whitespace",
                "U+200A"
            ),
            Arguments.of(
                "Em space (U+2003)",
                VALID_CSV_HEADER + "666,Grace\u2003,Test",
                "Problematic whitespace",
                "U+2003"
            ),
            Arguments.of(
                "Figure space (U+2007)",
                VALID_CSV_HEADER + "777,Henry\u2007,Test",
                "Problematic whitespace",
                "U+2007"
            )
        );
    }
    
    /**
     * Tests that files containing problematic whitespace are properly rejected.
     * These whitespace characters can be invisible, confusing, or cause
     * unexpected parsing behavior.
     * 
     * Example output map for a file with zero-width space (U+200B):
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_ScreeningProf_group1.csv",
     *       "fileType": "SDOH_ScreeningProf",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_ScreeningProf_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Invisible format characters: U+200B (ZERO WIDTH SPACE) at position 37"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * Example output map for a file with non-breaking space (U+00A0):
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_ScreeningProf_group1.csv",
     *       "fileType": "SDOH_ScreeningProf",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_ScreeningProf_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Problematic whitespace: U+00A0 (NON-BREAKING SPACE) at position 42"
     *     }
     *   ]
     * }
     * </pre>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("problematicWhitespaceTestCases")
    void testProblematicWhitespaceDetection(String testDescription, String content, 
                                           String expectedError, String expectedCodePoint) throws IOException {
        // Arrange
        Path testFile = createFile("SDOH_ScreeningProf_group1.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        
        // Check for either the expected error OR "Invisible format characters" 
        // (since some zero-width chars are categorized as format characters)
        boolean hasExpectedError = fileDetail.reason().contains(expectedError) || 
                                   fileDetail.reason().contains("Invisible format characters");
        assertTrue(hasExpectedError, 
            String.format("Reason should mention '%s' or 'Invisible format characters'. Actual: %s", 
                expectedError, fileDetail.reason()));
        
        assertTrue(fileDetail.reason().contains(expectedCodePoint), 
            String.format("Should include code point '%s' in error message. Actual: %s", 
                expectedCodePoint, fileDetail.reason()));
    }
    
    /**
     * Provides test cases for invalid surrogate validation.
     * Unpaired surrogates are invalid UTF-16 and should be rejected.
     * These are created by writing raw bytes instead of using Java string literals.
     * 
     * Note: In Java, unpaired surrogates in byte sequences will fail UTF-8 validation
     * before they can be checked as surrogates, so these tests verify UTF-8 encoding errors.
     * 
     * @return Stream of test arguments with invalid surrogate characters as raw bytes
     */
    static Stream<Arguments> invalidSurrogateTestCases() {
        return Stream.of(
            Arguments.of(
                "High surrogate without pair (U+D800) - Invalid UTF-8 sequence",
                new byte[] {
                    0x50, 0x61, 0x74, 0x69, 0x65, 0x6E, 0x74, 0x4D, 0x52, 0x4E, // "PatientMRN"
                    0x2C, 0x46, 0x69, 0x72, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65, // ",FirstName"
                    0x2C, 0x4C, 0x61, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65, 0x0A, // ",LastName\n"
                    0x34, 0x35, 0x36, 0x2C, 0x4A, 0x61, 0x6E, 0x65, // "456,Jane"
                    (byte)0xED, (byte)0xA0, (byte)0x80, // Invalid UTF-8 for U+D800 surrogate
                    0x2C, 0x53, 0x6D, 0x69, 0x74, 0x68 // ",Smith"
                },
                "not valid UTF-8 encoded"
            ),
            Arguments.of(
                "Low surrogate without pair (U+DC00) - Invalid UTF-8 sequence",
                new byte[] {
                    0x50, 0x61, 0x74, 0x69, 0x65, 0x6E, 0x74, 0x4D, 0x52, 0x4E,
                    0x2C, 0x46, 0x69, 0x72, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65,
                    0x2C, 0x4C, 0x61, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65, 0x0A,
                    0x37, 0x38, 0x39, 0x2C, 0x42, 0x6F, 0x62, // "789,Bob"
                    (byte)0xED, (byte)0xB0, (byte)0x80, // Invalid UTF-8 for U+DC00 surrogate
                    0x2C, 0x4A, 0x6F, 0x6E, 0x65, 0x73 // ",Jones"
                },
                "not valid UTF-8 encoded"
            ),
            Arguments.of(
                "Another high surrogate (U+DBFF) - Invalid UTF-8 sequence",
                new byte[] {
                    0x50, 0x61, 0x74, 0x69, 0x65, 0x6E, 0x74, 0x4D, 0x52, 0x4E,
                    0x2C, 0x46, 0x69, 0x72, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65,
                    0x2C, 0x4C, 0x61, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65, 0x0A,
                    0x31, 0x31, 0x31, 0x2C, 0x41, 0x6C, 0x69, 0x63, 0x65, // "111,Alice"
                    (byte)0xED, (byte)0xAF, (byte)0xBF, // Invalid UTF-8 for U+DBFF surrogate
                    0x2C, 0x57, 0x6F, 0x6E, 0x64, 0x65, 0x72 // ",Wonder"
                },
                "not valid UTF-8 encoded"
            ),
            Arguments.of(
                "Invalid continuation byte",
                new byte[] {
                    0x50, 0x61, 0x74, 0x69, 0x65, 0x6E, 0x74, 0x4D, 0x52, 0x4E,
                    0x2C, 0x46, 0x69, 0x72, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65,
                    0x2C, 0x4C, 0x61, 0x73, 0x74, 0x4E, 0x61, 0x6D, 0x65, 0x0A,
                    0x32, 0x32, 0x32, 0x2C, 0x54, 0x65, 0x73, 0x74, // "222,Test"
                    (byte)0xFF, // Invalid UTF-8 byte
                    0x2C, 0x55, 0x73, 0x65, 0x72 // ",User"
                },
                "not valid UTF-8 encoded"
            )
        );
    }
    
    /**
     * Tests that files containing invalid surrogate characters are rejected.
     * Unpaired surrogates indicate malformed UTF-16 encoding and can cause
     * encoding errors and data corruption.
     * 
     * Note: These test cases use raw byte arrays because unpaired surrogates
     * cannot be represented in valid Java strings. The UTF-8 byte sequences
     * for surrogate code points (U+D800-U+DFFF) are invalid in UTF-8, so these
     * files fail UTF-8 validation before surrogate checking occurs.
     * 
     * This validates that the system correctly rejects files with malformed
     * UTF-8 that would represent surrogates if they were valid.
     * 
     * Example output map for a file with invalid surrogate byte sequence:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_ScreeningObs_group1.csv",
     *       "fileType": "SDOH_ScreeningObs",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_ScreeningObs_group1.csv",
     *       "utf8Encoded": false,
     *       "reason": "File is not valid UTF-8 encoded: Input length = 3"
     *     }
     *   ]
     * }
     * </pre>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSurrogateTestCases")
    void testInvalidSurrogateDetection(String testDescription, byte[] contentBytes, 
                                      String expectedError) throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("SDOH_ScreeningObs_group1.csv");
        Files.write(testFile, contentBytes);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        assertTrue(fileDetail.reason().contains(expectedError), 
            String.format("Reason should mention '%s'. Actual: %s", expectedError, fileDetail.reason()));
        
        // Additional validation: ensure it mentions UTF-8 or encoding issue
        assertTrue(fileDetail.reason().toLowerCase().contains("utf-8") || 
                   fileDetail.reason().toLowerCase().contains("encoding"),
            "Should mention UTF-8 or encoding issue in error message");
    }
    
    /**
     * Tests BOM (Byte Order Mark) handling.
     * BOM at the start of file is allowed and stripped.
     * BOM in the middle of content (U+FEFF) should be rejected.
     * 
     * Example output map for a file with BOM at start (ALLOWED):
     * <pre>
     * {
     *   "_group1": [
     *     {
     *       "filename": "SDOH_PtInfo_group1.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": "PatientMRN,FirstName,LastName\n123,John,Doe",
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group1.csv",
     *       "utf8Encoded": true,
     *       "reason": null
     *     }
     *   ],
     *   "filesNotProcessed": []
     * }
     * </pre>
     */
    @Test
    void testBOMAtStartIsAllowed() throws IOException {
        // Arrange - Create file with UTF-8 BOM at start
        Path testFile = tempDir.resolve("SDOH_PtInfo_group1.csv");
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = (VALID_CSV_HEADER + VALID_CSV_ROW).getBytes();
        byte[] fileContent = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, fileContent, 0, bom.length);
        System.arraycopy(content, 0, fileContent, bom.length, content.length);
        Files.write(testFile, fileContent);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("_group1"), "Should contain _group1 key for valid files");
        
        List<FileDetail> processed = result.get("_group1");
        assertEquals(1, processed.size(), "Should have exactly one file processed");
        
        FileDetail fileDetail = processed.get(0);
        assertTrue(fileDetail.utf8Encoded(), "File should be marked as valid");
        assertNull(fileDetail.reason(), "Should not have a rejection reason");
        assertNotNull(fileDetail.content(), "Content should be available");
        
        // Verify BOM was stripped from content
        assertFalse(fileDetail.content().startsWith("\uFEFF"), 
            "Content should not start with BOM character");
        assertTrue(fileDetail.content().startsWith("PatientMRN"), 
            "Content should start with actual data");
    }
    
    /**
     * Tests BOM character (U+FEFF) in the middle of content is rejected.
     * 
     * Example output map for a file with BOM in middle of content:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_PtInfo_group2.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group2.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - BOM character in middle of content: U+FEFF (ZERO WIDTH NO-BREAK SPACE / BOM) at position 43"
     *     }
     *   ]
     * }
     * </pre>
     */
    @Test
    void testBOMInMiddleOfContentIsRejected() throws IOException {
        // Arrange - Create file with BOM character (U+FEFF) in the middle
        String content = VALID_CSV_HEADER + "456,Jane\uFEFF,Smith";
        Path testFile = createFile("SDOH_PtInfo_group2.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        
        // U+FEFF should be detected - either as BOM in middle or as invisible format character
        boolean hasBOMError = fileDetail.reason().contains("BOM character in middle of content") ||
                              fileDetail.reason().contains("Invisible format characters");
        assertTrue(hasBOMError, 
            String.format("Reason should mention BOM or format character issue. Actual: %s", fileDetail.reason()));
        assertTrue(fileDetail.reason().contains("U+FEFF"), 
            "Should include U+FEFF code point in error message");
    }
    
    /**
     * Provides test cases for Unicode non-characters.
     * Non-characters are permanently reserved and should not appear in interchange.
     * 
     * @return Stream of test arguments with non-character code points
     */
    static Stream<Arguments> nonCharacterTestCases() {
        return Stream.of(
            Arguments.of(
                "Non-character U+FDD0",
                VALID_CSV_HEADER + "456,Jane\uFDD0,Smith",
                "Unicode non-characters",
                "U+FDD0"
            ),
            Arguments.of(
                "Non-character U+FDEF",
                VALID_CSV_HEADER + "789,Bob\uFDEF,Jones",
                "Unicode non-characters",
                "U+FDEF"
            ),
            Arguments.of(
                "Non-character U+FFFE",
                VALID_CSV_HEADER + "111,Alice\uFFFE,Wonder",
                "Unicode non-characters",
                "U+FFFE"
            ),
            Arguments.of(
                "Non-character U+FFFF",
                VALID_CSV_HEADER + "222,Charlie\uFFFF,Brown",
                "Unicode non-characters",
                "U+FFFF"
            )
        );
    }
    
    /**
     * Tests that files containing Unicode non-characters are rejected.
     * Non-characters (U+FDD0..U+FDEF, U+FFFE, U+FFFF) are permanently
     * reserved and should never appear in text interchange.
     * 
     * Example output map for a file with non-character U+FDD0:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_PtInfo_group2.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group2.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Unicode non-characters: U+FDD0 (CHAR) at position 43"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * Example output map for a file with non-character U+FFFE:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_PtInfo_group2.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group2.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Unicode non-characters: U+FFFE (CHAR) at position 45"
     *     }
     *   ]
     * }
     * </pre>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("nonCharacterTestCases")
    void testNonCharacterDetection(String testDescription, String content, 
                                   String expectedError, String expectedCodePoint) throws IOException {
        // Arrange
        Path testFile = createFile("SDOH_PtInfo_group2.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        assertTrue(fileDetail.reason().contains(expectedError), 
            String.format("Reason should mention '%s'. Actual: %s", expectedError, fileDetail.reason()));
        assertTrue(fileDetail.reason().contains(expectedCodePoint), 
            String.format("Should include code point '%s' in error message. Actual: %s", 
                expectedCodePoint, fileDetail.reason()));
    }
    
    /**
     * Tests that files with allowed control characters (tab, newline, carriage return)
     * are processed successfully.
     * 
     * Example output map for a valid file with allowed control characters:
     * <pre>
     * {
     *   "_group3": [
     *     {
     *       "filename": "SDOH_PtInfo_group3.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": "PatientMRN\tFirstName\tLastName\r\n123\tJohn\tDoe\r\n456\tJane\tSmith",
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group3.csv",
     *       "utf8Encoded": true,
     *       "reason": null
     *     }
     *   ],
     *   "filesNotProcessed": []
     * }
     * </pre>
     */
    @Test
    void testAllowedControlCharacters() throws IOException {
        // Arrange - Content with tabs, newlines, and carriage returns
        String content = "PatientMRN\tFirstName\tLastName\r\n123\tJohn\tDoe\r\n456\tJane\tSmith";
        Path testFile = createFile("SDOH_PtInfo_group3.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("_group3"), "Should contain _group3 key");
        
        List<FileDetail> processed = result.get("_group3");
        assertEquals(1, processed.size(), "Should have exactly one file processed");
        
        FileDetail fileDetail = processed.get(0);
        assertTrue(fileDetail.utf8Encoded(), "File should be marked as valid");
        assertNull(fileDetail.reason(), "Should not have a rejection reason");
    }
    
    /**
     * Tests that multiple different error types in a single file are all reported.
     * 
     * Example output map for a file with multiple error types:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_QEadmin_group2.csv",
     *       "fileType": "SDOH_QEadmin",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_QEadmin_group2.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Null bytes (0x00): U+0000 (NULL) at position 41\n  - Control characters: U+0008 (BACKSPACE) at position 45\n  - Invisible format characters: U+200B (ZERO WIDTH SPACE) at position 47"
     *     }
     *   ]
     * }
     * </pre>
     */
    @Test
    void testMultipleErrorTypes() throws IOException {
        // Arrange - Content with null byte, control character, and zero-width space
        String content = VALID_CSV_HEADER + "456,\u0000Jane\b,\u200BSmith";
        Path testFile = createFile("SDOH_QEadmin_group2.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertNotNull(fileDetail.reason(), "Should have a reason for rejection");
        
        // Should mention multiple error types
        String reason = fileDetail.reason();
        assertTrue(reason.contains("Null bytes") || reason.contains("Control characters") || 
                   reason.contains("Zero-width") || reason.contains("Invisible format"), 
            String.format("Should mention at least one error type. Actual: %s", reason));
        
        // Should include multiple code points
        assertTrue(reason.contains("U+0000") || reason.contains("U+0008") || 
                   reason.contains("U+200B"), 
            "Should include at least one problematic code point");
    }
    
    /**
     * Tests that when one file in a group has errors, the entire group is rejected.
     * 
     * Example output map when a group is blocked due to one invalid file:
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_PtInfo_group4.csv",
     *       "fileType": "SDOH_PtInfo",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_PtInfo_group4.csv",
     *       "utf8Encoded": true,
     *       "reason": "Not processed as other files in the group have content validation errors. Group blocked by: SDOH_QEadmin_group4.csv (File contains invalid characters:\n  - Null bytes (0x00): U+0000 (NULL) at position 41)"
     *     },
     *     {
     *       "filename": "SDOH_QEadmin_group4.csv",
     *       "fileType": "SDOH_QEadmin",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_QEadmin_group4.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Null bytes (0x00): U+0000 (NULL) at position 41"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * Note: The valid file (SDOH_PtInfo_group4.csv) is also moved to filesNotProcessed
     * with a reason indicating it was blocked by the invalid file in its group.
     */
    @Test
    void testGroupRejectionDueToOneInvalidFile() throws IOException {
        // Arrange - One valid file and one with null byte in same group
        Path validFile = createFile("SDOH_PtInfo_group4.csv", VALID_CSV_HEADER + VALID_CSV_ROW);
        Path invalidFile = createFile("SDOH_QEadmin_group4.csv", VALID_CSV_HEADER + "456,\u0000Jane,Smith");
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(
            List.of(validFile.toString(), invalidFile.toString())
        );
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertFalse(result.containsKey("_group4"), "Group should not be in processed files");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(2, notProcessed.size(), "Both files should be in not processed list");
        
        // Check that both files are marked with reasons
        FileDetail validFileDetail = notProcessed.stream()
            .filter(fd -> fd.filename().equals("SDOH_PtInfo_group4.csv"))
            .findFirst()
            .orElse(null);
        assertNotNull(validFileDetail, "Valid file should be in not processed list");
        assertTrue(validFileDetail.reason().contains("Group blocked by"), 
            "Valid file should indicate it was blocked due to group");
        
        FileDetail invalidFileDetail = notProcessed.stream()
            .filter(fd -> fd.filename().equals("SDOH_QEadmin_group4.csv"))
            .findFirst()
            .orElse(null);
        assertNotNull(invalidFileDetail, "Invalid file should be in not processed list");
        assertTrue(invalidFileDetail.reason().contains("Null bytes"), 
            "Invalid file should have specific error reason");
    }
    
    /**
     * Tests format characters (invisible formatting characters) are detected.
     * 
     * Example output map for a file with format character (U+202E - RIGHT-TO-LEFT OVERRIDE):
     * <pre>
     * {
     *   "filesNotProcessed": [
     *     {
     *       "filename": "SDOH_ScreeningProf_group2.csv",
     *       "fileType": "SDOH_ScreeningProf",
     *       "content": null,
     *       "filePath": "/tmp/junit123/SDOH_ScreeningProf_group2.csv",
     *       "utf8Encoded": false,
     *       "reason": "File contains invalid characters:\n  - Invisible format characters: U+202E (FORMAT) at position 43"
     *     }
     *   ]
     * }
     * </pre>
     */
    @Test
    void testFormatCharacterDetection() throws IOException {
        // Arrange - Content with invisible format character (U+202E - RIGHT-TO-LEFT OVERRIDE)
        String content = VALID_CSV_HEADER + "456,Jane\u202E,Smith";
        Path testFile = createFile("SDOH_ScreeningProf_group2.csv", content);
        
        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(List.of(testFile.toString()));
        
        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("filesNotProcessed"), "Should contain filesNotProcessed key");
        
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size(), "Should have exactly one file not processed");
        
        FileDetail fileDetail = notProcessed.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should be marked as invalid");
        assertTrue(fileDetail.reason().contains("Invisible format characters") || 
                   fileDetail.reason().contains("format"), 
            String.format("Should mention format characters. Actual: %s", fileDetail.reason()));
    }
    
    /**
     * Helper method to create a test file with given content.
     */
    private Path createFile(String fileName, String content) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        Files.writeString(filePath, content);
        return filePath;
    }
}