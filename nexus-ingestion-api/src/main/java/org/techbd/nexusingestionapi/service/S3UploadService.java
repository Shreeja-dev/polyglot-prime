package org.techbd.nexusingestionapi.service;

import java.net.URI;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.config.AppConfig;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final AppConfig appConfig;

    public void uploadToS3(MultipartFile file) throws Exception {
        try {
            var s3Config = appConfig.getAws().getS3();
            String bucket = s3Config.getBucket();

            String originalFilename = file.getOriginalFilename();
            String key = "xxx_" + (originalFilename != null ? originalFilename : "unnamed");

            // Read bytes from MultipartFile
            byte[] content = file.getBytes();

            // Create S3 client
            S3Client s3 = S3Client.builder()
                    .endpointOverride(new URI(s3Config.getEndpoint()))
                    .region(Region.of(appConfig.getAws().getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .httpClient(UrlConnectionHttpClient.create())
                    .forcePathStyle(true)
                    .build();

            // Create bucket (idempotent in LocalStack)
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

            // Upload file
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                    RequestBody.fromBytes(content));

           
        } catch (Exception e) {
            System.err.println(" Failed to upload or download file: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void uploadToS3AndSqs(MultipartFile file) throws Exception {
        try {
            var s3Config = appConfig.getAws().getS3();
            var sqsConfig = appConfig.getAws().getSqs();
            String bucket = s3Config.getBucket();

            String originalFilename = file.getOriginalFilename();
            String key = "xxx_" + (originalFilename != null ? originalFilename : "unnamed");

            byte[] content = file.getBytes();

            // S3 client
            S3Client s3 = S3Client.builder()
                    .endpointOverride(new URI(s3Config.getEndpoint()))
                    .region(Region.of(appConfig.getAws().getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .httpClient(UrlConnectionHttpClient.create())
                    .forcePathStyle(true)
                    .build();

            // Create bucket (idempotent)
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());

            // Upload to S3
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                    RequestBody.fromBytes(content));

            SqsClient sqs = SqsClient.builder()
                    .endpointOverride(new URI(sqsConfig.getBaseUrl()))
                    .region(Region.of(appConfig.getAws().getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();

            // Send to SQS (for FIFO use messageGroupId & deduplication)
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsConfig.getFifoQueueUrl())
                    .messageBody(new String(content))
                    .messageGroupId("upload-group")
                    .messageDeduplicationId(UUID.randomUUID().toString())
                    .build());

            System.out.println("📨 Message sent to SQS: " + sqsConfig.getFifoQueueUrl());

        } catch (Exception e) {
            System.err.println("Failed to upload or send message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

}