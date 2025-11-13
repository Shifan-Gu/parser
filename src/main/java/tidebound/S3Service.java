package tidebound;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Service class to handle S3 operations for downloading replay files.
 * Supports both AWS S3 and S3-compatible services like MinIO.
 */
public class S3Service {
    private final S3Client s3Client;

    /**
     * Creates an S3Service with configuration from environment variables.
     * 
     * Environment variables:
     * - S3_ENDPOINT: Custom S3 endpoint (optional, for MinIO or other S3-compatible services)
     * - S3_REGION: AWS region (default: us-east-1)
     * - S3_ACCESS_KEY: AWS access key (optional, uses default credentials if not provided)
     * - S3_SECRET_KEY: AWS secret key (optional, uses default credentials if not provided)
     * - S3_PATH_STYLE_ACCESS: Use path-style access (true/false, default: false, set to true for MinIO)
     */
    public S3Service() {
        S3ClientBuilder builder = S3Client.builder();

        // Set region (default to us-east-1)
        String region = System.getenv().getOrDefault("S3_REGION", "us-east-1");
        builder.region(Region.of(region));

        // Set custom endpoint if provided (for MinIO or other S3-compatible services)
        String endpoint = System.getenv().get("S3_ENDPOINT");
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            System.err.println("Using S3 endpoint: " + endpoint);
        }

        // Set credentials if provided
        String accessKey = System.getenv().get("S3_ACCESS_KEY");
        String secretKey = System.getenv().get("S3_SECRET_KEY");
        if (accessKey != null && secretKey != null && !accessKey.isEmpty() && !secretKey.isEmpty()) {
            AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
            builder.credentialsProvider(credentialsProvider);
            System.err.println("Using custom S3 credentials");
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            System.err.println("Using default S3 credentials");
        }

        // Enable path-style access if specified (required for MinIO)
        String pathStyleAccess = System.getenv().get("S3_PATH_STYLE_ACCESS");
        if ("true".equalsIgnoreCase(pathStyleAccess)) {
            builder.forcePathStyle(true);
            System.err.println("Using S3 path-style access");
        }

        this.s3Client = builder.build();
    }

    /**
     * Downloads a file from S3.
     * 
     * @param s3Url The S3 URL in the format s3://bucket-name/key/path/to/file
     * @return InputStream containing the file data
     * @throws IOException if there's an error downloading the file
     */
    public InputStream downloadFromS3(String s3Url) throws IOException {
        try {
            // Parse S3 URL: s3://bucket-name/key/path/to/file
            if (!s3Url.startsWith("s3://")) {
                throw new IllegalArgumentException("Invalid S3 URL format. Expected: s3://bucket-name/key");
            }

            String path = s3Url.substring(5); // Remove "s3://"
            int firstSlash = path.indexOf('/');
            
            if (firstSlash == -1) {
                throw new IllegalArgumentException("Invalid S3 URL format. Expected: s3://bucket-name/key");
            }

            String bucket = path.substring(0, firstSlash);
            String key = path.substring(firstSlash + 1);

            System.err.println("Downloading from S3 - Bucket: " + bucket + ", Key: " + key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
            System.err.println("Successfully downloaded from S3");
            return response;

        } catch (Exception e) {
            throw new IOException("Failed to download from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a URL is an S3 URL.
     * 
     * @param url The URL to check
     * @return true if the URL starts with s3://, false otherwise
     */
    public static boolean isS3Url(String url) {
        return url != null && url.startsWith("s3://");
    }

    /**
     * Closes the S3 client and releases resources.
     */
    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }
}

