package com.engineeringproductivity.platform.common.persistence;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementKnowledgeModel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class KnowledgeModelConverter implements AttributeConverter<RequirementKnowledgeModel, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())  // needed for Java record constructor param names
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Tolerate new fields added to the schema — old stored JSON won't have them
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Missing fields in old JSON (e.g. requirementType, generationPlan) → null, not error
            .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(RequirementKnowledgeModel model) {
        if (model == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(model);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize RequirementKnowledgeModel", e);
        }
    }

    @Override
    public RequirementKnowledgeModel convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, RequirementKnowledgeModel.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize RequirementKnowledgeModel", e);
        }
    }
}
