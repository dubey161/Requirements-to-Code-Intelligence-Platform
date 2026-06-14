package com.engineeringproductivity.platform.fixloop;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Triggers the AI-driven fix loop: re-generates code, re-validates, re-pushes,
 * re-runs compliance + risk + AI review — all in one call.
 */
@RestController
@RequestMapping("/api/v1/requirements/{id}/apply-fixes")
public class ApplyFixesController {

    private final ApplyFixesService service;

    public ApplyFixesController(ApplyFixesService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    ApplyFixesService.FixResult applyFixes(
            @PathVariable UUID id,
            @RequestBody(required = false) ApplyFixesRequest request
    ) {
        String instructions = request != null ? request.customInstructions() : null;
        return service.applyFixes(id, instructions);
    }

    public record ApplyFixesRequest(String customInstructions) {}
}
