package csv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.frictionlessdata.datapackage.Package;
import io.frictionlessdata.tableschema.exception.ValidationException;

class DataPackageValidationTest {

    @Test
    void validateDataPackage() throws Exception {
        ValidationException exception = assertThrows(ValidationException.class, () -> this.getDataPackageFromFilePath(
                "org/techbd/csv/datapackage-nyher-fhir-ig-equivalent.json", true));

        // Assert the validation messages
        assertNotNull(exception.getMessages());
        assertFalse(exception.getMessages().isEmpty());
        String errorJson = convertToJson(exception.getMessages());
        System.out.println(errorJson);
    }

    @Test
    void validateDataPackage1() throws Exception {
        try {
            this.getDataPackageFromFilePath(
                    "org/techbd/csv/datapackage-nyher-fhir-ig-equivalent.json", true);
            fail("Expected a ValidationException to be thrown");
        } catch (ValidationException exception) {
            // Assert the validation messages
            assertNotNull(exception.getMessages());
            assertFalse(exception.getMessages().isEmpty());
            String errorJson = convertToJson(exception.getMessages());
            System.out.println(errorJson);
        } catch (Exception e) {
            // Handle any other exceptions
            e.printStackTrace();
            fail("Unexpected exception type: " + e.getClass().getName());
        }
    }

    public static Path getBasePath() {
        try {
            String pathName = "/org/techbd/csv/datapackage-nyher-fhir-ig-equivalent.json";
            Path sourceFileAbsPath = Paths.get(TestUtil.class.getResource(pathName).toURI());
            return sourceFileAbsPath.getParent();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Package getDataPackageFromFilePath(String datapackageFilePath, boolean strict) throws Exception {
        String jsonString = getFileContents(datapackageFilePath);
        Package dp = new Package(jsonString, getBasePath(), strict);
        return dp;
    }

    public String convertToJson(List<Object> validationMessages) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationMessages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    private Package getDataPackageFromFilePath(boolean strict) throws Exception {
        return this.getDataPackageFromFilePath("org/techbd/csv/datapackage-nyher-fhir-ig-equivalent.json", strict);
    }

    private static String getFileContents(String fileName) {
        try {
            return new String(TestUtil.getResourceContent(fileName));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}