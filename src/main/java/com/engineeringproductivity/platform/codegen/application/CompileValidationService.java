package com.engineeringproductivity.platform.codegen.application;

import com.engineeringproductivity.platform.codegen.domain.GeneratedFile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.Problem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates generated Java files for syntax correctness using JavaParser
 * before they are committed to GitHub.
 *
 * Uses AST parsing (not full compilation) — this catches all template/generation
 * bugs (malformed method signatures, unclosed blocks, bad syntax) without needing
 * the Spring/JPA classpath available at validation time.
 */
@Component
public class CompileValidationService {

    private static final Logger log = LoggerFactory.getLogger(CompileValidationService.class);

    private final JavaParser javaParser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_18));

    /**
     * Validates all generated files. Throws CompileValidationException if any
     * file has syntax errors — blocks PR creation.
     */
    public void validate(List<GeneratedFile> files) {
        List<ValidationError> errors = new ArrayList<>();

        for (GeneratedFile file : files) {
            List<ValidationError> fileErrors = validateFile(file);
            errors.addAll(fileErrors);
        }

        if (!errors.isEmpty()) {
            String summary = errors.stream()
                    .map(e -> "  %s:%d — %s".formatted(e.fileName(), e.line(), e.message()))
                    .reduce("", (a, b) -> a + "\n" + b);
            throw new CompileValidationException(
                    "Generated code has %d syntax error(s):%n%s".formatted(errors.size(), summary));
        }

        log.info("Compile validation passed for {} generated files", files.size());
    }

    private List<ValidationError> validateFile(GeneratedFile file) {
        List<ValidationError> errors = new ArrayList<>();

        ParseResult<CompilationUnit> result = javaParser.parse(file.content());

        for (Problem problem : result.getProblems()) {
            int line = problem.getLocation()
                    .flatMap(l -> l.getBegin().getRange())
                    .map(r -> r.begin.line)
                    .orElse(-1);

            errors.add(new ValidationError(file.fileName(), line, problem.getMessage()));
            log.warn("Syntax error in {}: line {} — {}", file.fileName(), line, problem.getMessage());
        }

        return errors;
    }

    public record ValidationError(String fileName, int line, String message) {}

    public static class CompileValidationException extends RuntimeException {
        public CompileValidationException(String message) {
            super(message);
        }
    }
}
