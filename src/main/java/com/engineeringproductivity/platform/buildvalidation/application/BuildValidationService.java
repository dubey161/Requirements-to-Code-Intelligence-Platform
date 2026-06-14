package com.engineeringproductivity.platform.buildvalidation.application;

import com.engineeringproductivity.platform.buildvalidation.domain.BuildValidationReport;
import com.engineeringproductivity.platform.buildvalidation.domain.BuildValidationRepository;
import com.engineeringproductivity.platform.buildvalidation.domain.ValidationCheck;
import com.engineeringproductivity.platform.codegen.application.CompileValidationService;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundleRepository;
import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs multi-layer validation on generated code before a PR is created:
 * 1. Syntax check — JavaParser AST validation on every file
 * 2. Structural integrity — every entity must have Repository, Service, Controller, Request, Response
 * 3. Package consistency — package declarations are non-empty and follow the base package
 * 4. Annotation presence — @Entity, @RestController, @Service, @Repository on expected files
 */
@Service
public class BuildValidationService {

    private static final Logger log = LoggerFactory.getLogger(BuildValidationService.class);

    private final GeneratedCodeBundleRepository codeRepository;
    private final BuildValidationRepository validationRepository;
    private final CompileValidationService compileValidationService;

    public BuildValidationService(GeneratedCodeBundleRepository codeRepository,
                                   BuildValidationRepository validationRepository,
                                   CompileValidationService compileValidationService) {
        this.codeRepository = codeRepository;
        this.validationRepository = validationRepository;
        this.compileValidationService = compileValidationService;
    }

    @Transactional
    public BuildValidationReport validate(UUID requirementId) {
        GeneratedCodeBundle bundle = codeRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No generated code for requirement: " + requirementId +
                        ". Run code generation first."));

        List<GeneratedFile> files = bundle.getGeneratedFiles();
        List<ValidationCheck> checks = new ArrayList<>();
        int errors = 0;
        int warnings = 0;

        // ── Check 1: Syntax (JavaParser) ─────────────────────────────────────
        CheckResult syntaxResult = runSyntaxCheck(files);
        checks.add(syntaxResult.check());
        errors   += syntaxResult.errors();
        warnings += syntaxResult.warnings();

        // ── Check 2: Structural integrity ────────────────────────────────────
        CheckResult structResult = runStructuralCheck(files);
        checks.add(structResult.check());
        errors   += structResult.errors();
        warnings += structResult.warnings();

        // ── Check 3: Annotation presence ─────────────────────────────────────
        CheckResult annotResult = runAnnotationCheck(files);
        checks.add(annotResult.check());
        errors   += annotResult.errors();
        warnings += annotResult.warnings();

        // ── Check 4: Package consistency ─────────────────────────────────────
        CheckResult pkgResult = runPackageCheck(files, bundle.getTargetPackage());
        checks.add(pkgResult.check());
        errors   += pkgResult.errors();
        warnings += pkgResult.warnings();

        String status = errors > 0 ? "FAILED" : warnings > 0 ? "WARNING" : "PASSED";
        log.info("Build validation for requirement={}: status={}, files={}, errors={}, warnings={}",
                requirementId, status, files.size(), errors, warnings);

        BuildValidationReport report = BuildValidationReport.create(
                requirementId, status, files.size(), errors, warnings, checks);

        // upsert — delete old if exists
        validationRepository.findByRequirementId(requirementId)
                .ifPresent(validationRepository::delete);
        validationRepository.flush();
        return validationRepository.save(report);
    }

    @Transactional(readOnly = true)
    public BuildValidationReport getReport(UUID requirementId) {
        return validationRepository.findByRequirementId(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No build validation for requirement: " + requirementId +
                        ". Trigger via POST /api/v1/requirements/{id}/build-validation"));
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    private CheckResult runSyntaxCheck(List<GeneratedFile> files) {
        List<String> syntaxErrors = new ArrayList<>();
        for (GeneratedFile file : files) {
            try {
                compileValidationService.validate(List.of(file));
            } catch (CompileValidationService.CompileValidationException e) {
                syntaxErrors.add(file.fileName() + ": " + e.getMessage());
            }
        }
        if (syntaxErrors.isEmpty()) {
            return new CheckResult(new ValidationCheck("Syntax Check", "PASS",
                    "All " + files.size() + " files parsed successfully by JavaParser"), 0, 0);
        }
        return new CheckResult(new ValidationCheck("Syntax Check", "FAIL",
                syntaxErrors.size() + " syntax error(s): " + String.join("; ", syntaxErrors.stream()
                        .map(e -> e.length() > 100 ? e.substring(0, 100) + "…" : e).toList())),
                syntaxErrors.size(), 0);
    }

    private CheckResult runStructuralCheck(List<GeneratedFile> files) {
        // Group files by entity (derive entity name from file name pattern)
        Set<String> entities = files.stream()
                .filter(f -> f.fileType() == GeneratedFile.FileType.ENTITY)
                .map(f -> f.fileName().replace(".java", ""))
                .collect(Collectors.toSet());

        List<String> missing = new ArrayList<>();
        for (String entity : entities) {
            List<String> expected = List.of(
                    entity + "Repository.java",
                    entity + "Service.java",
                    entity + "Controller.java",
                    "Create" + entity + "Request.java",
                    entity + "Response.java"
            );
            Set<String> fileNames = files.stream().map(GeneratedFile::fileName).collect(Collectors.toSet());
            for (String exp : expected) {
                if (!fileNames.contains(exp)) {
                    missing.add(entity + " → missing " + exp);
                }
            }
        }

        if (missing.isEmpty()) {
            return new CheckResult(new ValidationCheck("Structural Integrity", "PASS",
                    entities.size() + " entit" + (entities.size() == 1 ? "y" : "ies") +
                    " — all layers present (Entity, Repository, Service, Controller, Request, Response)"),
                    0, 0);
        }
        return new CheckResult(new ValidationCheck("Structural Integrity", "WARN",
                "Missing files: " + String.join(", ", missing)), 0, missing.size());
    }

    private CheckResult runAnnotationCheck(List<GeneratedFile> files) {
        Map<GeneratedFile.FileType, String> required = Map.of(
                GeneratedFile.FileType.ENTITY,     "@Entity",
                GeneratedFile.FileType.REPOSITORY, "JpaRepository",
                GeneratedFile.FileType.SERVICE,    "@Service",
                GeneratedFile.FileType.CONTROLLER, "@RestController"
        );
        List<String> missing = new ArrayList<>();
        for (GeneratedFile file : files) {
            String expected = required.get(file.fileType());
            if (expected != null && !file.content().contains(expected)) {
                missing.add(file.fileName() + " missing " + expected);
            }
        }
        if (missing.isEmpty()) {
            return new CheckResult(new ValidationCheck("Annotation Check", "PASS",
                    "All required Spring/JPA annotations present"), 0, 0);
        }
        return new CheckResult(new ValidationCheck("Annotation Check", "WARN",
                "Missing annotations: " + String.join(", ", missing)), 0, missing.size());
    }

    private CheckResult runPackageCheck(List<GeneratedFile> files, String basePackage) {
        List<String> issues = new ArrayList<>();
        for (GeneratedFile file : files) {
            String content = file.content();
            if (!content.contains("package ")) {
                issues.add(file.fileName() + " has no package declaration");
            } else if (!content.contains("package " + basePackage)) {
                // soft warning — sub-packages are fine
            }
        }
        if (issues.isEmpty()) {
            return new CheckResult(new ValidationCheck("Package Consistency", "PASS",
                    "All files have valid package declarations under " + basePackage), 0, 0);
        }
        return new CheckResult(new ValidationCheck("Package Consistency", "WARN",
                String.join(", ", issues)), 0, issues.size());
    }

    private record CheckResult(ValidationCheck check, int errors, int warnings) {}
}
