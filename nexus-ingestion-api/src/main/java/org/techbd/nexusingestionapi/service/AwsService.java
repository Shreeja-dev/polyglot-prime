package org.techbd.nexusingestionapi.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.nexusingestionapi.commons.Constants;
import org.techbd.nexusingestionapi.controller.DataIngestionController;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Service
public class AwsService {

    private final S3Client s3Client;
    private static final Logger LOG = LoggerFactory.getLogger(DataIngestionController.class.getName());
    public AwsService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String extractTenantId(Map<String, String> headers) {
        // return headers.getFirst(Constants.REQ_HEADER_TENANT_ID); // Using Constants.TENANT_ID_HEADER
        return headers.getOrDefault(Constants.REQ_HEADER_TENANT_ID, Constants.DEFAULT_TENANT_ID);
    }

    public Map<String, String> extractMetadata(MultipartFile file) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("Content-Type", file.getContentType());
        metadata.put("Original-Filename", file.getOriginalFilename());
        metadata.put("Size", String.valueOf(file.getSize()));
        return metadata;
    }

    public String saveToS3(String bucketName,Map<String, String> headers, MultipartFile file, String interactionId) {
        LOG.info("Saving file to S3 for interactionId BEGIN: {}", interactionId);
        String tenantId = extractTenantId(headers);
        Map<String, String> metadata = extractMetadata(file);

        try {
            String key = "uploads/" + tenantId + "/" + file.getOriginalFilename();
            // Check if bucket exists, create if not
            try {
                s3Client.headBucket(b -> b.bucket(bucketName));
            } catch (software.amazon.awssdk.services.s3.model.NoSuchBucketException e) {
                LOG.info("Bucket {} does not exist. Creating...", bucketName);
                s3Client.createBucket(b -> b.bucket(bucketName));
            } catch (Exception e) {
                LOG.error("Error checking/creating bucket: {}", e.getMessage(), e);
                throw new RuntimeException("Error checking/creating S3 bucket", e);
            }
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName) // Using bucketName
                    .key(key)
                    .metadata(metadata)
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(file.getBytes())
            );

            final var responseStr = "File uploaded successfully to S3: " + key + " (ETag: " + response.eTag() + ")";
            LOG.info("Saving file to S3 for interactionId END: {}", interactionId);
            return responseStr;
        } catch (IOException e) {
            LOG.error("Error uploading file to S3 for interactionId {}: tenantId={}, fileName={}, error={}",
                interactionId, tenantId, file.getOriginalFilename(), e.toString(), e);
            throw new RuntimeException("Error uploading file to S3", e);
        }
    }

    public void saveToS3(String bucketName, String fileName, String content, Map<String, String> metadata, String interactionId) {
        LOG.info("Saving meta data to S3 for interactionId BEGIN: {}", interactionId);
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            try {
                s3Client.headBucket(b -> b.bucket(bucketName));
            } catch (software.amazon.awssdk.services.s3.model.NoSuchBucketException e) {
                LOG.info("Bucket {} does not exist. Creating...", bucketName);
                s3Client.createBucket(b -> b.bucket(bucketName));
            } catch (Exception e) {
                LOG.error("Error checking/creating bucket: {}", e.getMessage(), e);
                throw new RuntimeException("Error checking/creating S3 bucket", e);
            }
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .metadata(metadata)
                .build();

            s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(contentBytes));
        } catch (Exception e) {
            LOG.error("Error saving meta data to S3 for interactionId {}: bucketName={}, fileName={},  error={}",
                interactionId, bucketName, fileName, e.toString(),e);
            //throw new RuntimeException("Error saving meta data to S3", e); //TODO -check if we need to throw exception
        }
        LOG.info("Saving meta data to S3 for interactionId END: {}", interactionId);
    }

    // public String uploadMultipartFile(MultipartFile file, Map<String, String> headers) {
    //     return saveToS3(headers, file);
    // }
}
