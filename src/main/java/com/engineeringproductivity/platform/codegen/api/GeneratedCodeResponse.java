package com.engineeringproductivity.platform.codegen.api;

import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GeneratedCodeResponse(
        UUID bundleId,
        UUID requirementId,
        String targetPackage,
        int fileCount,
        List<GeneratedFileDetail> files,
        Instant generatedAt
) {
    public record GeneratedFileDetail(
            String fileName,
            String fullPath,
            String fileType,
            String content
    ) {}

    public static GeneratedCodeResponse from(GeneratedCodeBundle bundle) {
        List<GeneratedFileDetail> details = bundle.getGeneratedFiles().stream()
                .map(f -> new GeneratedFileDetail(
                        f.fileName(), f.fullPath(), f.fileType().name(), f.content()))
                .toList();

        return new GeneratedCodeResponse(
                bundle.getId(),
                bundle.getRequirementId(),
                bundle.getTargetPackage(),
                bundle.getGeneratedFiles().size(),
                details,
                bundle.getGeneratedAt()
        );
    }
}
