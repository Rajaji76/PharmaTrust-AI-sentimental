package pharmatrust.manufacturing_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * AWS SDK v2 configuration for S3 and Secrets Manager
 */
@Configuration
public class AwsConfig {

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    /**
     * Configure S3 client for lab report storage
     */
    @Bean
    public S3Client s3Client() {
        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            // Use default credentials provider chain (IAM role, environment variables, etc.)
            return S3Client.builder()
                .region(Region.of(region))
                .build();
        }

        // Use explicit credentials
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }

    /**
     * Configure Secrets Manager client for key management
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            // Use default credentials provider chain
            return SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();
        }

        // Use explicit credentials
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return SecretsManagerClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }
}
