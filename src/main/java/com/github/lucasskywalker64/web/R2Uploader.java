package com.github.lucasskywalker64.web;

import com.github.lucasskywalker64.BotMain;
import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

public class R2Uploader {

    private final S3Client client;
    private final String BUCKET_NAME;

    public R2Uploader() {
        Dotenv config = BotMain.getContext().config();
        BUCKET_NAME = config.get("S3_CLOUD_BUCKET");
        String accessId = config.get("S3_CLOUD_ID");
        String accessSecret = config.get("S3_CLOUD_SECRET");
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessId, accessSecret)
        );

        client = S3Client.builder()
                .endpointOverride(URI.create(config.get("S3_CLOUD_URL")))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    public void uploadAttachment(
            String key,
            InputStream in,
            long contentLength,
            String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();

        RequestBody requestBody = RequestBody.fromInputStream(in, contentLength);
        
        client.putObject(req, requestBody);
    }
}
