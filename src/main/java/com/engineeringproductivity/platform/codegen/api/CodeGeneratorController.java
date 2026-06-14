package com.engineeringproductivity.platform.codegen.api;

import com.engineeringproductivity.platform.codegen.application.CodeGeneratorService;
import com.engineeringproductivity.platform.codegen.domain.GeneratedCodeBundle;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements/{requirementId}/code")
public class CodeGeneratorController {

    private final CodeGeneratorService service;

    public CodeGeneratorController(CodeGeneratorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    GeneratedCodeResponse generate(@PathVariable UUID requirementId) {
        GeneratedCodeBundle bundle = service.generate(requirementId);
        return GeneratedCodeResponse.from(bundle);
    }

    @GetMapping
    GeneratedCodeResponse getCode(@PathVariable UUID requirementId) {
        GeneratedCodeBundle bundle = service.getBundle(requirementId);
        return GeneratedCodeResponse.from(bundle);
    }
}
