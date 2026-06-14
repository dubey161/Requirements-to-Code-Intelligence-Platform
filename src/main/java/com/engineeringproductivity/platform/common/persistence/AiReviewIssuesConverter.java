package com.engineeringproductivity.platform.common.persistence;

import com.engineeringproductivity.platform.aireview.domain.AiReviewIssue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class AiReviewIssuesConverter implements AttributeConverter<List<AiReviewIssue>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<List<AiReviewIssue>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<AiReviewIssue> issues) {
        if (issues == null) return null;
        try { return MAPPER.writeValueAsString(issues); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to serialize AiReviewIssue list", e); }
    }

    @Override
    public List<AiReviewIssue> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, TYPE); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to deserialize AiReviewIssue list", e); }
    }
}
