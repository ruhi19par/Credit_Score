package com.credbridge.backend.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class LlmScoringClient {

    private static final Logger log = LoggerFactory.getLogger(LlmScoringClient.class);
    public static final String PROMPT_VERSION = "credit-risk-v1";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public LlmScoringClient(
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:llama-3.1-8b-instant}") String model,
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(apiKey);
                    return execution.execute(request, body);
                })
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    public Optional<ScoringDecision> evaluate(LlmScoringRequest scoringRequest) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", 0.1,
                    "max_completion_tokens", 600,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", userPrompt(scoringRequest))
                    )
            );

            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return Optional.empty();
            }

            String content = response.choices().getFirst().message().content();
            String json = extractJson(content);
            LlmScoringResponse parsed = objectMapper.readValue(json, LlmScoringResponse.class);
            return Optional.of(toDecision(parsed, json));
        } catch (Exception ex) {
            log.warn("LLM scoring failed; using rules-based scoring explanation", ex);
            return Optional.empty();
        }
    }

    private String systemPrompt() {
        return """
                You are a lending risk explanation assistant. You must analyze only the numbers supplied by the backend.
                Do not invent, estimate, or modify financial values. The numeric credit score and risk level are already
                calculated by rules and must be treated as authoritative.

                Return only valid JSON with these keys:
                riskExplanation: string,
                confidenceScore: number from 0 to 1,
                defaultRisk: number from 0 to 1,
                lendingRecommendation: one of APPROVE, MANUAL_REVIEW, DECLINE,
                additionalRiskFactors: array of strings,
                positiveFactors: array of strings.
                """;
    }

    private String userPrompt(LlmScoringRequest request) throws JsonProcessingException {
        return "Prompt version: " + PROMPT_VERSION + "\n"
                + "Evaluate this calculated lending risk profile and return JSON only:\n"
                + objectMapper.writeValueAsString(request);
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("LLM response content is empty");
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("LLM response did not contain a JSON object");
        }
        return content.substring(start, end + 1);
    }

    private ScoringDecision toDecision(LlmScoringResponse response, String rawResponse) {
        String recommendation = normalizeRecommendation(response.lendingRecommendation());
        BigDecimal confidence = clampRatio(response.confidenceScore());
        BigDecimal defaultRisk = clampRatio(response.defaultRisk());
        String explanation = StringUtils.hasText(response.riskExplanation())
                ? response.riskExplanation()
                : "LLM returned no explanation.";
        return new ScoringDecision(
                explanation,
                confidence,
                defaultRisk,
                recommendation,
                model,
                PROMPT_VERSION,
                rawResponse,
                explanation
        );
    }

    private String normalizeRecommendation(String recommendation) {
        if (!StringUtils.hasText(recommendation)) {
            return "MANUAL_REVIEW";
        }
        String normalized = recommendation.trim().toUpperCase();
        return switch (normalized) {
            case "APPROVE", "MANUAL_REVIEW", "DECLINE" -> normalized;
            default -> "MANUAL_REVIEW";
        };
    }

    private BigDecimal clampRatio(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }
}
