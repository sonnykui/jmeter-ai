package org.qainsights.jmeter.ai.utils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import com.anthropic.models.ModelInfo;
import com.anthropic.models.ModelListPage;
import com.anthropic.models.ModelListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.Model;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Models {
    private static final Logger log = LoggerFactory.getLogger(Models.class);

    /**
     * Get a combined list of model IDs from both Anthropic and OpenAI
     * @param anthropicClient Anthropic client
     * @param openAiClient OpenAI client
     * @return List of model IDs
     */
    public static List<String> getModelIds(AnthropicClient anthropicClient, OpenAIClient openAiClient) {
        List<String> modelIds = new ArrayList<>();
        
        try {
            // Get Anthropic models
            List<String> anthropicModels = getAnthropicModelIds(anthropicClient);
            if (anthropicModels != null) {
                modelIds.addAll(anthropicModels);
            }
            
            // Get OpenAI models
            List<String> openAiModels = getOpenAiModelIds(openAiClient);
            if (openAiModels != null) {
                modelIds.addAll(openAiModels);
            }
            
            log.info("Combined {} models from Anthropic and OpenAI", modelIds.size());
            return modelIds;
        } catch (Exception e) {
            log.error("Error combining models: {}", e.getMessage(), e);
            return modelIds; // Return whatever we have, even if empty
        }
    }
    
    /**
     * Get Anthropic models as ModelListPage
     * @param client Anthropic client
     * @return ModelListPage containing Anthropic models
     */
    public static ModelListPage getAnthropicModels(AnthropicClient client) {
        return getAnthropicModels(client, "anthropic");
    }

    /**
     * Get Anthropic models as ModelListPage with service type support
     * @param client Anthropic client (can be null if serviceType is bedrock)
     * @param serviceType Service type ("anthropic" or "bedrock")
     * @return ModelListPage containing Anthropic models
     */
    public static ModelListPage getAnthropicModels(AnthropicClient client, String serviceType) {
        if ("bedrock".equalsIgnoreCase(serviceType)) {
            return getAnthropicModelsFromBedrock();
        } else {
            return getAnthropicModelsFromAnthropic(client);
        }
    }

    /**
     * Get Anthropic models directly from Anthropic API
     * @param client Anthropic client
     * @return ModelListPage containing Anthropic models
     */
    private static ModelListPage getAnthropicModelsFromAnthropic(AnthropicClient client) {
        try {
            log.info("Fetching available models from Anthropic API");
            client = AnthropicOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("anthropic.api.key", "YOUR_API_KEY"))
                    .build();

            ModelListParams modelListParams = ModelListParams.builder().build();
            ModelListPage models = client.models().list(modelListParams);
            
            log.info("Successfully retrieved {} models from Anthropic API", models.data().size());
            for (ModelInfo model : models.data()) {
                log.debug("Available Anthropic model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from Anthropic API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get Anthropic models from AWS Bedrock
     * @return ModelListPage containing Anthropic models from Bedrock
     */
    static ModelListPage getAnthropicModelsFromBedrock() {
        try {
            // Clear any previous Bedrock models to ensure clean state
            bedrockModels = null;
            log.info("Fetching available Claude models from AWS Bedrock");
            
            // Get AWS region configuration
            String region = AiConfig.getProperty("bedrock.region", "us-east-1");
            
            // Create Bedrock client using DefaultCredentialsProvider
            BedrockClient bedrockClient = BedrockClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.builder().build())
                    .build();
            log.info("Using DefaultCredentialsProvider for Bedrock model listing in region: {}", region);

            // List foundation models
            ListFoundationModelsRequest request = ListFoundationModelsRequest.builder()
                    .byProvider("Anthropic")
                    .build();
            
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(request);
            
            // Convert Bedrock models to Anthropic ModelInfo format
            List<ModelInfo> modelInfoList = response.modelSummaries().stream()
                    .filter(model -> model.modelId().toLowerCase().contains("claude") || 
                                   model.modelId().toLowerCase().contains("anthropic"))
                    .map(Models::convertBedrockModelToModelInfo)
                    .collect(Collectors.toList());
            
            log.info("Successfully retrieved {} Claude models from AWS Bedrock", modelInfoList.size());
            for (ModelInfo model : modelInfoList) {
                log.debug("Available Bedrock Claude model: {}", model.id());
            }
            
            // Since we can't create a proper ModelListPage, we'll create a mock Anthropic client call
            // and inject our models into the response. For now, return null and handle this in the callers.
            return createMockModelListPageWithBedrockModels(modelInfoList);
            
        } catch (Exception e) {
            log.error("Error fetching models from AWS Bedrock: {}", e.getMessage(), e);
            // Clear the static field when credentials are invalid
            bedrockModels = null;
            return null;
        }
    }

    /**
     * Converts a Bedrock FoundationModelSummary to Anthropic ModelInfo
     * @param bedrockModel Bedrock model summary
     * @return Anthropic ModelInfo
     */
    private static ModelInfo convertBedrockModelToModelInfo(FoundationModelSummary bedrockModel) {
        // Create a ModelInfo object with the Bedrock model data
        return ModelInfo.builder()
                .id(bedrockModel.modelId())
                .displayName(bedrockModel.modelName())
                .createdAt(java.time.OffsetDateTime.now())
                .build();
    }

    /**
     * Creates a mock ModelListPage with Bedrock models
     * Since ModelListPage is final and cannot be extended, we'll return null 
     * and handle the Bedrock models differently in the caller methods.
     * @param models List of ModelInfo objects from Bedrock
     * @return null (caller should handle this case specifically)
     */
    private static ModelListPage createMockModelListPageWithBedrockModels(List<ModelInfo> models) {
        // Store the models in a static field that can be accessed by getAnthropicModelIds
        bedrockModels = models;
        return null; // Signal that this is a Bedrock response
    }
    
    // Static field to store Bedrock models when we can't create a proper ModelListPage
    private static List<ModelInfo> bedrockModels = null;
    
    /**
     * Clear the static bedrockModels field - used for testing to ensure isolation
     */
    public static void clearBedrockModels() {
        bedrockModels = null;
    }
    
    /**
     * Get Anthropic model IDs as a List of Strings
     * @param client Anthropic client
     * @return List of model IDs
     */
    public static List<String> getAnthropicModelIds(AnthropicClient client) {
        return getAnthropicModelIds(client, "anthropic");
    }

    /**
     * Get Anthropic model IDs as a List of Strings with service type support
     * @param client Anthropic client (can be null if serviceType is bedrock)
     * @param serviceType Service type ("anthropic" or "bedrock")
     * @return List of model IDs
     */
    public static List<String> getAnthropicModelIds(AnthropicClient client, String serviceType) {
        ModelListPage models = getAnthropicModels(client, serviceType);
        if (models != null && models.data() != null) {
            return models.data().stream()
                    .map(ModelInfo::id)
                    .collect(Collectors.toList());
        } else if ("bedrock".equalsIgnoreCase(serviceType) && bedrockModels != null) {
            // Handle the Bedrock case where we return null from getAnthropicModels
            return bedrockModels.stream()
                    .map(ModelInfo::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    
    /**
     * Get OpenAI models as ModelListPage
     * @param client OpenAI client
     * @return OpenAI ModelListPage
     */
    public static com.openai.models.ModelListPage getOpenAiModels(OpenAIClient client) {
        try {
            log.info("Fetching available models from OpenAI API");
            client = OpenAIOkHttpClient.builder()
                    .apiKey(AiConfig.getProperty("openai.api.key", "YOUR_API_KEY"))
                    .build();            

            com.openai.models.ModelListPage models = client.models().list();
            
            log.info("Successfully retrieved {} models from OpenAI API", models.data().size());
            for (Model model : models.data()) {
                log.debug("Available OpenAI model: {}", model.id());
            }
            return models;
        } catch (Exception e) {
            log.error("Error fetching models from OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get OpenAI model IDs as a List of Strings
     * @param client OpenAI client
     * @return List of model IDs
     */
    public static List<String> getOpenAiModelIds(OpenAIClient client) {
        com.openai.models.ModelListPage models = getOpenAiModels(client);
        if (models != null && models.data() != null) {
            // Return the list of GPT models only, excluding audio and TTS models
            return models.data().stream()
                    .filter(model -> model.id().startsWith("gpt")) // Include only GPT models
                    .filter(model -> !model.id().contains("audio")) // Exclude audio models
                    .filter(model -> !model.id().contains("tts")) // Exclude text-to-speech models
                    .filter(model -> !model.id().contains("whisper")) // Exclude whisper models
                    .filter(model -> !model.id().contains("davinci")) // Exclude Davinci models
                    .filter(model -> !model.id().contains("search")) // Exclude search models
                    .filter(model -> !model.id().contains("transcribe")) // Exclude transcribe models
                    .filter(model -> !model.id().contains("realtime")) // Exclude realtime models
                    .filter(model -> !model.id().contains("instruct")) // Exclude instruct models
                    .map(com.openai.models.Model::id)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
