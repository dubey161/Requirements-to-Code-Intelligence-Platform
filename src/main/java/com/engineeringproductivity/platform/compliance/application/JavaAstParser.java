package com.engineeringproductivity.platform.compliance.application;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Java source code from PR diffs using JavaParser AST.
 * Extracts actual classes, annotations, and mappings — far more accurate
 * than string matching against file names or patch text.
 */
@Component
public class JavaAstParser {

    private static final Logger log = LoggerFactory.getLogger(JavaAstParser.class);
    private static final Pattern DIFF_ADDED_LINE = Pattern.compile("^\\+(?!\\+\\+)(.+)$", Pattern.MULTILINE);

    private final JavaParser javaParser = new JavaParser();

    public ParsedPrContent parse(List<com.engineeringproductivity.platform.github.GitHubClient.PrFile> prFiles) {
        Set<String> entityClasses = new HashSet<>();
        Set<String> repositoryInterfaces = new HashSet<>();
        Set<String> serviceClasses = new HashSet<>();
        Set<String> controllerClasses = new HashSet<>();
        Set<String> requestMappingPaths = new HashSet<>();
        Set<String> validationAnnotations = new HashSet<>();
        Set<String> securityAnnotations = new HashSet<>();
        List<String> parseErrors = new ArrayList<>();

        for (var file : prFiles) {
            if (!file.filename().endsWith(".java")) continue;

            String addedCode = extractAddedLines(file.patch());
            if (addedCode.isBlank()) continue;

            // Wrap in a class if it looks like a fragment (from diff) for parsing
            String parseable = addedCode.contains("class ") || addedCode.contains("interface ")
                    ? addedCode
                    : "class _Fragment { " + addedCode + " }";

            ParseResult<CompilationUnit> result = javaParser.parse(parseable);
            if (!result.isSuccessful()) {
                // Fallback: annotation extraction via regex on the raw added lines
                extractAnnotationsViaRegex(addedCode, validationAnnotations, securityAnnotations,
                        requestMappingPaths);
                continue;
            }

            CompilationUnit cu = result.getResult().orElse(null);
            if (cu == null) continue;

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                List<String> annotations = cls.getAnnotations().stream()
                        .map(AnnotationExpr::getNameAsString).toList();

                if (annotations.contains("Entity")) {
                    entityClasses.add(cls.getNameAsString());
                }
                if (annotations.contains("Repository") || cls.getNameAsString().endsWith("Repository")) {
                    repositoryInterfaces.add(cls.getNameAsString());
                }
                if (annotations.contains("Service")) {
                    serviceClasses.add(cls.getNameAsString());
                }
                if (annotations.contains("RestController") || annotations.contains("Controller")) {
                    controllerClasses.add(cls.getNameAsString());
                    // Extract @RequestMapping value from class
                    cls.getAnnotationByName("RequestMapping")
                            .ifPresent(ann -> extractMappingValue(ann.toString(), requestMappingPaths));
                }

                // Check field/method level annotations
                cls.findAll(AnnotationExpr.class).forEach(ann -> {
                    String name = ann.getNameAsString();
                    if (isValidationAnnotation(name)) validationAnnotations.add(name);
                    if (isSecurityAnnotation(name)) securityAnnotations.add(name);
                });

                // Extract method-level mappings
                cls.findAll(MethodDeclaration.class).forEach(method ->
                        method.getAnnotations().forEach(ann -> {
                            String name = ann.getNameAsString();
                            if (name.endsWith("Mapping")) {
                                extractMappingValue(ann.toString(), requestMappingPaths);
                            }
                        }));
            });
        }

        return new ParsedPrContent(entityClasses, repositoryInterfaces, serviceClasses,
                controllerClasses, requestMappingPaths, validationAnnotations,
                securityAnnotations, parseErrors);
    }

    private String extractAddedLines(String patch) {
        if (patch == null || patch.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = DIFF_ADDED_LINE.matcher(patch);
        while (m.find()) {
            sb.append(m.group(1)).append("\n");
        }
        return sb.toString();
    }

    private void extractMappingValue(String annotationStr, Set<String> paths) {
        // Extract string literal from @RequestMapping("/api/v1/users") or @PostMapping("/")
        Pattern p = Pattern.compile("\"(/[^\"]+)\"");
        Matcher m = p.matcher(annotationStr);
        while (m.find()) paths.add(m.group(1));
    }

    private void extractAnnotationsViaRegex(String code, Set<String> validations,
                                             Set<String> security, Set<String> paths) {
        Pattern ann = Pattern.compile("@(\\w+)");
        Matcher m = ann.matcher(code);
        while (m.find()) {
            String name = m.group(1);
            if (isValidationAnnotation(name)) validations.add(name);
            if (isSecurityAnnotation(name)) security.add(name);
        }
        extractMappingValue(code, paths);
    }

    private boolean isValidationAnnotation(String name) {
        return switch (name) {
            case "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max",
                 "Email", "Pattern", "Positive", "PositiveOrZero",
                 "Negative", "NegativeOrZero", "Valid", "Validated" -> true;
            default -> false;
        };
    }

    private boolean isSecurityAnnotation(String name) {
        return switch (name) {
            case "PreAuthorize", "PostAuthorize", "Secured",
                 "RolesAllowed", "PermitAll", "DenyAll",
                 "EnableMethodSecurity", "EnableGlobalMethodSecurity" -> true;
            default -> false;
        };
    }

    public record ParsedPrContent(
            Set<String> entityClasses,
            Set<String> repositoryInterfaces,
            Set<String> serviceClasses,
            Set<String> controllerClasses,
            Set<String> requestMappingPaths,
            Set<String> validationAnnotations,
            Set<String> securityAnnotations,
            List<String> parseErrors
    ) {}
}
