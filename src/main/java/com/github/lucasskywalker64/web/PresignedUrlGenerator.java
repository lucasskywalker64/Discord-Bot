package com.github.lucasskywalker64.web;

import com.github.lucasskywalker64.BotMain;
import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

public class PresignedUrlGenerator {

    private final S3Presigner presigner;
    private final String BUCKET_NAME;

    public PresignedUrlGenerator() {
        Dotenv config = BotMain.getContext().config();
        BUCKET_NAME = config.get("S3_CLOUD_BUCKET");
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.get("S3_CLOUD_ID"),
                config.get("S3_CLOUD_SECRET")
        );
        presigner = S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(config.get("S3_CLOUD_URL")))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    public String createPresignedUrl(String object) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(object)
                .responseContentDisposition("attachment")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }
}
