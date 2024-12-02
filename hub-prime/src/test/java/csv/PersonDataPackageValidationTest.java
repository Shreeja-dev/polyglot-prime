package csv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.frictionlessdata.datapackage.Package;
import io.frictionlessdata.tableschema.exception.ValidationException;

class PersonDataPackageValidationTest {

    @Test
    void validateDataPackage() throws Exception {
        // Validate the datapackage.json using the new resource paths
        ValidationException exception = assertThrows(ValidationException.class, () -> this.getDataPackageFromFilePath(
                "org/techbd/csv/datapackage.json", true));

        // Assert the validation messages
        assertNotNull(exception.getMessages());
        assertFalse(exception.getMessages().isEmpty());

        // Convert validation messages to JSON and save them to a file
        String errorJson = convertToJson(exception.getMessages());
        final var filePath = "src/test/resources/csv/output/person-error.json"; // Update output path if needed
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, errorJson);
    }

    // Get the base path for resources (for the new datapackage.json)
    public static Path getBasePath() {
        try {
            String pathName = "/org/techbd/csv/datapackage.json";
            Path sourceFileAbsPath = Paths.get(DataPackageValidationTest.class.getResource(pathName).toURI());
            return sourceFileAbsPath.getParent();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // Method to load and parse the DataPackage JSON file
    private Package getDataPackageFromFilePath(String datapackageFilePath, boolean strict) throws Exception {
        String jsonString = getFileContents(datapackageFilePath);
        Package dp = new Package(jsonString, getBasePath(), strict);
        return dp;
    }

    // Method to convert validation messages to JSON format
    public String convertToJson(List<Object> validationMessages) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationMessages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    // Method to load file contents
    private static String getFileContents(String fileName) {
        try {
            return new String(TestUtil.getResourceContent(fileName));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

