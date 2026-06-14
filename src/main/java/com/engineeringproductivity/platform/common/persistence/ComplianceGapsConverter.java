package com.engineeringproductivity.platform.common.persistence;

import com.engineeringproductivity.platform.compliance.domain.ComplianceGap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ComplianceGapsConverter implements AttributeConverter<List<ComplianceGap>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final TypeReference<List<ComplianceGap>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ComplianceGap> gaps) {
        if (gaps == null) return null;
        try { return MAPPER.writeValueAsString(gaps); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to serialize ComplianceGap list", e); }
    }

    @Override
    public List<ComplianceGap> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, TYPE); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to deserialize ComplianceGap list", e); }
    }
}
