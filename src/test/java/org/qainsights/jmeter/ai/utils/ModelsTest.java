package org.qainsights.jmeter.ai.utils;

import com.anthropic.models.ModelInfo;
import com.anthropic.models.ModelListPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Models class, specifically testing AWS Bedrock integration
 */
public class ModelsTest {
    private static final Logger log = LoggerFactory.getLogger(ModelsTest.class);

    @BeforeEach
    void setUp() {
        // Reset any static state before each test
        // This ensures tests don't interfere with each other
        
        // Clear the bedrockModels static field to ensure test isolation
        Models.clearBedrockModels();
        log.debug("Cleared bedrockModels static field for test isolation");
    }

    /**
     * Test getAnthropicModelsFromBedrock with actual AWS credentials.
     * This test is disabled by default and only runs when AWS credentials are provided
     * via system properties.
     * 
     * To run this test, set the following system properties:
     * -Dtest.aws.access.key=YOUR_ACCESS_KEY
     * -Dtest.aws.secret.key=YOUR_SECRET_KEY
     * -Dtest.aws.region=us-east-1
     * -Dtest.bedrock.integration=true
     */
    @Test
    @EnabledIfSystemProperty(named = "test.bedrock.integration", matches = "true")
    void testGetAnthropicModelsFromBedrock_WithRealCredentials() {
        // Get test credentials from system properties
        String accessKey = System.getProperty("test.aws.access.key");
        String secretKey = System.getProperty("test.aws.secret.key");
        String region = System.getProperty("test.aws.region", "us-east-1");

        assertNotNull(accessKey, "AWS access key must be provided via -Dtest.aws.access.key");
        assertNotNull(secretKey, "AWS secret key must be provided via -Dtest.aws.secret.key");
        assertFalse(accessKey.isEmpty(), "AWS access key cannot be empty");
        assertFalse(secretKey.isEmpty(), "AWS secret key cannot be empty");

        log.info("Testing Bedrock integration with region: {}", region);
        log.info("Access key starts with: {}***", accessKey.substring(0, Math.min(4, accessKey.length())));

        // Set up test configuration by temporarily overriding AiConfig properties
        // We'll use reflection to set the properties for this test
        try {
            // Store original values to restore later
            String originalAccessKey = AiConfig.getProperty("bedrock.access.key", "");
            String originalSecretKey = AiConfig.getProperty("bedrock.secret.key", "");
            String originalRegion = AiConfig.getProperty("bedrock.region", "us-east-1");

            // Set test properties
            System.setProperty("bedrock.access.key", accessKey);
            System.setProperty("bedrock.secret.key", secretKey);
            System.setProperty("bedrock.region", region);

            // Test the method
            log.info("Calling getAnthropicModelsFromBedrock()...");
            ModelListPage result = Models.getAnthropicModelsFromBedrock();

            // For Bedrock integration, the method returns null by design and stores models in a static field
            // This is expected behavior due to ModelListPage being final and not mockable
            log.info("Result from getAnthropicModelsFromBedrock(): {}", result != null ? "ModelListPage object" : "null (expected for Bedrock)");
            
            // The real test is whether we can retrieve model IDs via the alternative method
            // For Bedrock, the method returns null but should populate the static field
            log.info("Testing model IDs retrieval via getAnthropicModelIds...");
            java.util.List<String> modelIds = Models.getAnthropicModelIds(null, "bedrock");
            assertNotNull(modelIds, "Model IDs list should not be null");
            
            if (result == null) {
                // This is the expected path for Bedrock integration
                log.info("Result is null (expected for Bedrock) - checking static field models");
                assertFalse(modelIds.isEmpty(), "Model IDs list should not be empty - at least some Claude models should be available");
                
                log.info("Successfully retrieved {} Claude model IDs from Bedrock", modelIds.size());
                for (String modelId : modelIds) {
                    log.info("Available model: {}", modelId);
                    assertTrue(modelId.toLowerCase().contains("claude") || 
                              modelId.toLowerCase().contains("anthropic"), 
                              "Model ID should contain 'claude' or 'anthropic': " + modelId);
                }
            } else {
                // If we get a proper ModelListPage, verify its contents
                assertNotNull(result.data(), "Model data should not be null");
                assertFalse(result.data().isEmpty(), "Should have at least one Claude model available");
                
                log.info("Successfully retrieved {} Claude models from Bedrock", result.data().size());
                for (ModelInfo model : result.data()) {
                    log.info("Available model: {} - {}", model.id(), model.displayName());
                    assertNotNull(model.id(), "Model ID should not be null");
                    assertTrue(model.id().toLowerCase().contains("claude") || 
                              model.id().toLowerCase().contains("anthropic"), 
                              "Model ID should contain 'claude' or 'anthropic': " + model.id());
                }
            }

            // Restore original properties
            if (!originalAccessKey.isEmpty()) {
                System.setProperty("bedrock.access.key", originalAccessKey);
            } else {
                System.clearProperty("bedrock.access.key");
            }
            
            if (!originalSecretKey.isEmpty()) {
                System.setProperty("bedrock.secret.key", originalSecretKey);
            } else {
                System.clearProperty("bedrock.secret.key");
            }
            
            if (!originalRegion.equals("us-east-1")) {
                System.setProperty("bedrock.region", originalRegion);
            } else {
                System.clearProperty("bedrock.region");
            }

        } catch (Exception e) {
            log.error("Test failed with exception", e);
            fail("Test should not throw exception with valid AWS credentials: " + e.getMessage());
        }
    }

    /**
     * Test getAnthropicModelsFromBedrock with invalid credentials.
     * This should handle the error gracefully and return null.
     */
    @Test
    void testGetAnthropicModelsFromBedrock_WithInvalidCredentials() {
        log.info("Testing Bedrock integration with invalid credentials");

        // Set invalid credentials
        System.setProperty("bedrock.access.key", "invalid-access-key");
        System.setProperty("bedrock.secret.key", "invalid-secret-key");
        System.setProperty("bedrock.region", "us-east-1");

        try {
            ModelListPage result = Models.getAnthropicModelsFromBedrock();
            
            // With invalid credentials, the method should return null and log an error
            assertNull(result, "Result should be null when AWS credentials are invalid");
            
            // Also test the model IDs retrieval
            java.util.List<String> modelIds = Models.getAnthropicModelIds(null, "bedrock");
            assertNotNull(modelIds, "Model IDs list should not be null even with invalid credentials");
            assertTrue(modelIds.isEmpty(), "Model IDs list should be empty with invalid credentials");
            
        } catch (Exception e) {
            // The method should handle exceptions gracefully and not throw them
            fail("Method should handle invalid credentials gracefully without throwing exceptions: " + e.getMessage());
        } finally {
            // Clean up test properties
            System.clearProperty("bedrock.access.key");
            System.clearProperty("bedrock.secret.key");
            System.clearProperty("bedrock.region");
        }
    }

    /**
     * Test getAnthropicModelsFromBedrock with empty credentials.
     * This should use the default credential provider chain.
     */
    @Test
    void testGetAnthropicModelsFromBedrock_WithEmptyCredentials() {
        log.info("Testing Bedrock integration with empty credentials (default provider chain)");

        // Set empty credentials to test default provider chain
        System.setProperty("bedrock.access.key", "");
        System.setProperty("bedrock.secret.key", "");
        System.setProperty("bedrock.region", "us-east-1");

        try {
            ModelListPage result = Models.getAnthropicModelsFromBedrock();
            
            // With empty credentials, it will try default provider chain
            // Result depends on whether default AWS credentials are available
            // We don't assert specific outcomes since this depends on the environment
            
            log.info("Result with default provider chain: {}", result != null ? "Success" : "Failed/No credentials");
            
            // Test should not throw exceptions regardless of credential availability
            
        } catch (Exception e) {
            // Log the exception but don't fail the test since default credentials may not be available
            log.warn("Expected behavior - default credential provider may not have credentials: {}", e.getMessage());
        } finally {
            // Clean up test properties
            System.clearProperty("bedrock.access.key");
            System.clearProperty("bedrock.secret.key");
            System.clearProperty("bedrock.region");
        }
    }

    /**
     * Enhanced debug version of the Bedrock test with detailed logging and diagnostics
     */
    @Test
    @EnabledIfSystemProperty(named = "test.bedrock.integration", matches = "true")
    void testGetAnthropicModelsFromBedrock_DebugVersion() {
        String accessKey = System.getProperty("test.aws.access.key");
        String secretKey = System.getProperty("test.aws.secret.key");
        String region = System.getProperty("test.aws.region", "us-east-1");

        log.info("=== BEDROCK DEBUG TEST STARTING ===");
        log.info("Java Version: {}", System.getProperty("java.version"));
        log.info("AWS SDK Version: {}", software.amazon.awssdk.core.SdkSystemSetting.AWS_REGION.environmentVariable());
        log.info("Test Region: {}", region);
        log.info("Access Key Length: {}", accessKey != null ? accessKey.length() : "null");
        log.info("Secret Key Length: {}", secretKey != null ? secretKey.length() : "null");

        // Verify credentials are provided
        assertNotNull(accessKey, "AWS access key must be provided via -Dtest.aws.access.key");
        assertNotNull(secretKey, "AWS secret key must be provided via -Dtest.aws.secret.key");

        // Set test properties with debug logging
        log.info("Setting system properties for test...");
        System.setProperty("bedrock.access.key", accessKey);
        System.setProperty("bedrock.secret.key", secretKey);
        System.setProperty("bedrock.region", region);

        try {
            log.info("=== CALLING getAnthropicModelsFromBedrock() ===");
            
            // Call the method with detailed error catching
            ModelListPage result = null;
            Exception caughtException = null;
            
            try {
                result = Models.getAnthropicModelsFromBedrock();
                log.info("Method returned successfully: {}", result != null ? "ModelListPage object" : "null");
            } catch (Exception e) {
                caughtException = e;
                log.error("Exception caught during method call", e);
            }

            // Detailed result analysis
            if (result != null) {
                log.info("=== RESULT ANALYSIS ===");
                log.info("Result type: {}", result.getClass().getName());
                log.info("Has data: {}", result.data() != null);
                if (result.data() != null) {
                    log.info("Model count: {}", result.data().size());
                    for (int i = 0; i < result.data().size(); i++) {
                        ModelInfo model = result.data().get(i);
                        log.info("Model {}: ID={}, Name={}", i, model.id(), model.displayName());
                    }
                }
            } else {
                log.info("=== NULL RESULT - CHECKING ALTERNATIVE APPROACH ===");
                // Test the alternative approach via getAnthropicModelIds
                try {
                    java.util.List<String> modelIds = Models.getAnthropicModelIds(null, "bedrock");
                    log.info("Alternative method returned {} model IDs", modelIds.size());
                    for (int i = 0; i < modelIds.size(); i++) {
                        log.info("Model ID {}: {}", i, modelIds.get(i));
                    }
                    
                    if (!modelIds.isEmpty()) {
                        log.info("SUCCESS: Retrieved models via alternative method");
                        // Test passes if we get models via the alternative method
                        assertTrue(true, "Successfully retrieved models via getAnthropicModelIds");
                    } else {
                        fail("No models retrieved via any method");
                    }
                } catch (Exception e) {
                    log.error("Alternative method also failed", e);
                    throw e;
                }
            }

            if (caughtException != null) {
                log.error("=== EXCEPTION ANALYSIS ===");
                log.error("Exception type: {}", caughtException.getClass().getName());
                log.error("Exception message: {}", caughtException.getMessage());
                log.error("Exception cause: {}", caughtException.getCause());
                
                // Re-throw for test failure
                throw new RuntimeException("Test failed with exception", caughtException);
            }

        } finally {
            // Clean up
            log.info("=== CLEANING UP TEST PROPERTIES ===");
            System.clearProperty("bedrock.access.key");
            System.clearProperty("bedrock.secret.key");
            System.clearProperty("bedrock.region");
            log.info("=== BEDROCK DEBUG TEST COMPLETED ===");
        }
    }

    /**
     * Test the getAnthropicModels method with bedrock service type.
     * This tests the public API that routes to the Bedrock implementation.
     */
    @Test
    @EnabledIfSystemProperty(named = "test.bedrock.integration", matches = "true")
    void testGetAnthropicModels_BedrockServiceType() {
        String accessKey = System.getProperty("test.aws.access.key");
        String secretKey = System.getProperty("test.aws.secret.key");
        String region = System.getProperty("test.aws.region", "us-east-1");

        assertNotNull(accessKey, "AWS access key must be provided via -Dtest.aws.access.key");
        assertNotNull(secretKey, "AWS secret key must be provided via -Dtest.aws.secret.key");

        // Set test properties
        System.setProperty("bedrock.access.key", accessKey);
        System.setProperty("bedrock.secret.key", secretKey);
        System.setProperty("bedrock.region", region);

        try {
            // Test the public API method
            ModelListPage result = Models.getAnthropicModels(null, "bedrock");
            
            // The behavior should be the same as calling getAnthropicModelsFromBedrock directly
            // Result may be null due to ModelListPage creation limitations
            log.info("Public API result: {}", result != null ? "ModelListPage created" : "Using static field approach");
            
            // Test model IDs retrieval which should work regardless
            java.util.List<String> modelIds = Models.getAnthropicModelIds(null, "bedrock");
            assertNotNull(modelIds, "Model IDs should not be null");
            assertFalse(modelIds.isEmpty(), "Should retrieve at least some Claude models");
            
            log.info("Retrieved {} models via public API", modelIds.size());
            
        } catch (Exception e) {
            fail("Public API should not throw exception with valid credentials: " + e.getMessage());
        } finally {
            // Clean up
            System.clearProperty("bedrock.access.key");
            System.clearProperty("bedrock.secret.key");
            System.clearProperty("bedrock.region");
        }
    }
}