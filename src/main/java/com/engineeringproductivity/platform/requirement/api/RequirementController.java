package com.engineeringproductivity.platform.requirement.api;

import com.engineeringproductivity.platform.auth.domain.User;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.application.RequirementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService service;

    public RequirementController(RequirementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequirementResponse create(@Valid @RequestBody CreateRequirementRequest request,
                               @AuthenticationPrincipal User currentUser) {
        RequirementStory story = service.create(
                request.externalKey(),
                request.title(),
                request.description(),
                request.acceptanceCriteria(),
                currentUser.getId()
        );
        return RequirementResponse.from(story);
    }

    @GetMapping("/{id}")
    RequirementResponse get(@PathVariable UUID id) {
        return RequirementResponse.from(service.get(id));
    }
}
