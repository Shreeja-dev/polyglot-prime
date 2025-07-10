package org.techbd.nexusingestionapi.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.techbd.config.AppConfig;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsConfig {

    // @Bean
    // public S3Client s3Client() {
    //     return S3Client.builder()
    //             .region(Region.US_EAST_1) // Change this to your AWS region
    //             .credentialsProvider(DefaultCredentialsProvider.create())
    //             .build();
    // }
    @Bean
    public S3Client s3Client(AppConfig appConfig) {
        return S3Client.builder()
            .endpointOverride(URI.create(appConfig.getAws().getS3().getEndpoint()))
            .region(Region.of(appConfig.getAws().getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(appConfig.getAws().getAccessKey(), appConfig.getAws().getSecretKey())
            ))
            .build();
    }
    @Bean
    public SqsClient sqsClient(AppConfig appConfig) {
        return SqsClient.builder()
            .endpointOverride(URI.create(appConfig.getAws().getSqs().getEndpoint()))
            .region(Region.of(appConfig.getAws().getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(appConfig.getAws().getAccessKey(), appConfig.getAws().getSecretKey())
            ))
            .build();
    }
}
