package com.engineeringproductivity.platform.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Document(indexName = "platform-requirements")
public class RequirementSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String externalKey;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Text)
    private List<String> acceptanceCriteria;

    @Field(type = FieldType.Keyword)
    private String status;

    // Extracted from knowledge model for search
    @Field(type = FieldType.Keyword)
    private List<String> entityNames;

    @Field(type = FieldType.Text)
    private List<String> endpointPaths;

    @Field(type = FieldType.Text)
    private List<String> validationFields;

    @Field(type = FieldType.Keyword)
    private List<String> securityCategories;

    // Risk and compliance metadata
    @Field(type = FieldType.Keyword)
    private String riskLevel;

    @Field(type = FieldType.Integer)
    private Integer riskScore;

    @Field(type = FieldType.Integer)
    private Integer complianceScore;

    @Field(type = FieldType.Keyword)
    private String prUrl;

    // Semantic embedding vector (768 dims — Gemini text-embedding-004)
    // index=true required for KNN search in ES 8+
    @Field(type = FieldType.Dense_Vector, dims = 768, index = true)
    private float[] embedding;

    @Field(type = FieldType.Date)
    private Instant indexedAt;

    public RequirementSearchDocument() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<String> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getEntityNames() { return entityNames; }
    public void setEntityNames(List<String> entityNames) { this.entityNames = entityNames; }
    public List<String> getEndpointPaths() { return endpointPaths; }
    public void setEndpointPaths(List<String> endpointPaths) { this.endpointPaths = endpointPaths; }
    public List<String> getValidationFields() { return validationFields; }
    public void setValidationFields(List<String> validationFields) { this.validationFields = validationFields; }
    public List<String> getSecurityCategories() { return securityCategories; }
    public void setSecurityCategories(List<String> securityCategories) { this.securityCategories = securityCategories; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public Integer getComplianceScore() { return complianceScore; }
    public void setComplianceScore(Integer complianceScore) { this.complianceScore = complianceScore; }
    public String getPrUrl() { return prUrl; }
    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
