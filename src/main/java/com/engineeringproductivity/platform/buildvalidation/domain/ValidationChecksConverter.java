package com.engineeringproductivity.platform.buildvalidation.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ValidationChecksConverter implements AttributeConverter<List<ValidationCheck>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ValidationCheck>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ValidationCheck> checks) {
        if (checks == null) return "[]";
        try { return MAPPER.writeValueAsString(checks); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to serialize ValidationCheck list", e); }
    }

    @Override
    public List<ValidationCheck> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, TYPE); }
        catch (Exception e) { throw new IllegalArgumentException("Failed to deserialize ValidationCheck list", e); }
    }
}
