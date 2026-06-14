package com.engineeringproductivity.platform.codegen.domain;

import com.engineeringproductivity.platform.common.persistence.GeneratedFilesConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "generated_code_bundle")
public class GeneratedCodeBundle {

    @Id
    private UUID id;

    @Column(name = "requirement_id", nullable = false, unique = true)
    private UUID requirementId;

    @Convert(converter = GeneratedFilesConverter.class)
    @Column(name = "generated_files", nullable = false, columnDefinition = "text")
    private List<GeneratedFile> generatedFiles;

    @Column(name = "target_package", nullable = false, length = 200)
    private String targetPackage;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected GeneratedCodeBundle() {}

    private GeneratedCodeBundle(UUID id, UUID requirementId, List<GeneratedFile> generatedFiles,
                                 String targetPackage, Instant generatedAt) {
        this.id = id;
        this.requirementId = requirementId;
        this.generatedFiles = generatedFiles;
        this.targetPackage = targetPackage;
        this.generatedAt = generatedAt;
    }

    public static GeneratedCodeBundle create(UUID requirementId, List<GeneratedFile> files, String targetPackage) {
        return new GeneratedCodeBundle(UUID.randomUUID(), requirementId, files, targetPackage, Instant.now());
    }

    public void refresh(List<GeneratedFile> files) {
        this.generatedFiles = files;
        this.generatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRequirementId() { return requirementId; }
    public List<GeneratedFile> getGeneratedFiles() { return generatedFiles; }
    public String getTargetPackage() { return targetPackage; }
    public Instant getGeneratedAt() { return generatedAt; }
}
