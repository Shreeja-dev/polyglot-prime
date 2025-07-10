package org.techbd.nexusingestionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.techbd.config.AppConfig;
import org.techbd.nexusingestionapi.service.AwsService;
import org.techbd.nexusingestionapi.service.S3UploadService;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@WebMvcTest(DataIngestionController.class)
@Import(DataIngestionControllerFileTypesTest.TestConfig.class)
@ActiveProfiles("sandbox")
public class DataIngestionControllerFileTypesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppConfig appConfig;

    static Stream<String> fileNames() {
        return Stream.of(
                "test123.csv", "test123.pdf", "test123.png",
                "test123.zip", "test123.json", "test123.hl7", "test123.xml");
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void shouldDownloadFileFromS3AndWriteToLocal(String fileName) throws Exception {
        ClassPathResource resource = new ClassPathResource("org/techbd/examples/" + fileName);
        byte[] content = Files.readAllBytes(resource.getFile().toPath());

        // Generate key as "xxx_filename"
        String key = "xxx_" + fileName;
        String bucket = "local-bucket";

        // Create S3 client pointing to LocalStack
        S3Client s3 = S3Client.builder()
                .endpointOverride(new URI("http://localhost:4566"))
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .forcePathStyle(true)
                .build();

        // Create bucket (idempotent)
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

        // Upload file
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(Files.probeContentType(resource.getFile().toPath()))
                .build(),
                RequestBody.fromBytes(content));

        // Verify key exists
        ListObjectsV2Response list = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build());
        String downloadedKey = list.contents().stream()
                .map(S3Object::key)
                .filter(k -> k.equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Key not found in S3: " + key));

        // Read object
        ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(downloadedKey)
                .build());

        // Create target path (download folder)
        Path downloadDir = Paths.get("src/test/resources/org/techbd/examples/downloaded");
        Files.createDirectories(downloadDir);
        Path targetFile = downloadDir.resolve(fileName);

        // Write stream to file
        Files.copy(response, targetFile, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Downloaded S3 file: " + key + " -> " + targetFile.toAbsolutePath());
        System.out.println(" File path: " + targetFile.toAbsolutePath());
        System.out.println(" File exists: " + Files.exists(targetFile));
        // Verify downloaded file exists and has content
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.size(targetFile)).isGreaterThan(0);
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void shouldIngestFileViaEndpointAndDownloadFromS3(String fileName) throws Exception {
        // 1. Load file from classpath
        ClassPathResource resource = new ClassPathResource("org/techbd/examples/" + fileName);
        byte[] content = Files.readAllBytes(resource.getFile().toPath());

        // 2. Create MockMultipartFile
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", fileName,
                Files.probeContentType(resource.getFile().toPath()), content);

        // 3. Call /ingest endpoint
        mockMvc.perform(MockMvcRequestBuilders.multipart("/ingest").file(multipartFile))
                .andExpect(status().isOk());

        // 4. S3 setup
        String expectedKey = "xxx_" + fileName;
        String bucket = "local-bucket";

        S3Client s3 = S3Client.builder()
                .endpointOverride(new URI("http://localhost:4566"))
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClient(UrlConnectionHttpClient.create())
                .forcePathStyle(true)
                .build();

        // 5. List and match object
        Thread.sleep(500); // Small delay if needed
        ListObjectsV2Response list = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build());
        String uploadedKey = list.contents().stream()
                .map(S3Object::key)
                .filter(k -> k.equals(expectedKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Uploaded key not found in S3: " + expectedKey));

        // 6. Download the file
        ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(uploadedKey)
                .build());

        Path downloadDir = Paths.get("src/test/resources/org/techbd/examples/downloadedtest");
        Files.createDirectories(downloadDir);
        Path targetFile = downloadDir.resolve(fileName);
        Files.copy(response, targetFile, StandardCopyOption.REPLACE_EXISTING);

        System.out.println(" Downloaded to: " + targetFile.toAbsolutePath());

        // 7. Assert file exists and matches
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.size(targetFile)).isGreaterThan(0);

        String downloadedContent = new String(Files.readAllBytes(targetFile)).replaceAll("\\r?\\n", "").trim();
        String originalContent = new String(content).replaceAll("\\r?\\n", "").trim();
        assertThat(downloadedContent).contains(originalContent);
    }
@ParameterizedTest
@MethodSource("fileNames")
void shouldIngestFileViaEndpointAndDownloadFromSqs(String fileName) throws Exception {
    // 1. Load file
    ClassPathResource resource = new ClassPathResource("org/techbd/examples/" + fileName);
    byte[] content = Files.readAllBytes(resource.getFile().toPath());

    // 2. Mock file
    MockMultipartFile multipartFile = new MockMultipartFile(
        "file", fileName,
        Files.probeContentType(resource.getFile().toPath()), content
    );

    // 3. Call /ingest endpoint
    mockMvc.perform(MockMvcRequestBuilders.multipart("/ingest").file(multipartFile))
        .andExpect(status().isOk());

    // 4. Setup SQS client
    SqsClient sqs = SqsClient.builder()
        .endpointOverride(new URI("http://localhost:4566"))
        .region(Region.of("us-east-1"))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .httpClient(UrlConnectionHttpClient.create())
        .build();

    String queueUrl = "http://localhost:4566/000000000000/local-queue.fifo";

    // 5. Poll SQS (basic long polling)
    ReceiveMessageResponse receiveResponse = sqs.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .waitTimeSeconds(5)
        .maxNumberOfMessages(1)
        .build());

    // 6. Validate and write message to file
    String messageBody = receiveResponse.messages().stream()
        .findFirst()
        .map(Message::body)
        .orElseThrow(() -> new AssertionError("No message found in SQS"));

    Path downloadDir = Paths.get("src/test/resources/org/techbd/examples/downloadedfromsqs");
    Files.createDirectories(downloadDir);
    Path targetFile = downloadDir.resolve(fileName);
    Files.write(targetFile, messageBody.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    System.out.println("📩 Downloaded message body from SQS to: " + targetFile.toAbsolutePath());

    // 7. Compare content
    String downloaded = Files.readString(targetFile).replaceAll("\\r?\\n", "").trim();
    String original = new String(content).replaceAll("\\r?\\n", "").trim();
    assertThat(downloaded).contains(original);
}
    @TestConfiguration
    static class TestConfig {

        // @Bean
        // public AppConfig appConfig(org.springframework.core.env.Environment environment) {
        //     AppConfig.Aws.S3 s3 = new AppConfig.Aws.S3(
        //             environment.getProperty("org.techbd.aws.s3.bucket", "local-bucket"),
        //             environment.getProperty("org.techbd.aws.s3.endpoint", "http://localhost:4566"));

        //     AppConfig.Aws.Sqs sqs = new AppConfig.Aws.Sqs(
        //             environment.getProperty("org.techbd.aws.sqs.base-url", "http://localhost:4566/000000000000"),
        //             environment.getProperty("org.techbd.aws.sqs.fifo-queue-url",
        //                     "http://localhost:4566/000000000000/local-queue.fifo"));

        //     AppConfig.Aws aws = new AppConfig.Aws(
        //             environment.getProperty("org.techbd.aws.region", "us-east-1"),
        //             environment.getProperty("org.techbd.aws.secret-name", "dummy-secret"),
        //             s3,
        //             sqs);

        //     return new AppConfig(aws);
        // }

        @Bean
        public S3UploadService s3UploadService(AppConfig appConfig) {
            return new S3UploadService(appConfig);
        }

        @Bean
        public software.amazon.awssdk.services.s3.S3Client s3Client() {
            return org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class);
        }

        @Bean
        public AwsService awsService(software.amazon.awssdk.services.s3.S3Client s3Client) {
            return new AwsService(s3Client);
        }

        @Bean
        public SqsClient sqsClient() {
            return mock(SqsClient.class);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}