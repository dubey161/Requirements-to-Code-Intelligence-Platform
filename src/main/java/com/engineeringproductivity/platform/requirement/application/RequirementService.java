package com.engineeringproductivity.platform.requirement.application;

import com.engineeringproductivity.platform.common.api.ResourceConflictException;
import com.engineeringproductivity.platform.common.api.ResourceNotFoundException;
import com.engineeringproductivity.platform.common.async.RequirementCreatedEvent;
import com.engineeringproductivity.platform.requirement.domain.RequirementStory;
import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RequirementService {

    private final RequirementStoryRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public RequirementService(RequirementStoryRepository repository,
                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a requirement and returns immediately with ANALYSIS_PENDING status.
     * Analysis is triggered asynchronously via RequirementCreatedEvent after commit.
     */
    @Transactional
    public RequirementStory create(String externalKey, String title,
                                    String description, List<String> acceptanceCriteria) {
        return create(externalKey, title, description, acceptanceCriteria, null);
    }

    @Transactional
    public RequirementStory create(String externalKey, String title, String description,
                                    List<String> acceptanceCriteria, UUID createdBy) {
        if (repository.existsByExternalKey(externalKey)) {
            throw new ResourceConflictException("Requirement story already exists: " + externalKey);
        }
        RequirementStory story = repository.save(
                RequirementStory.receive(externalKey, title, description, acceptanceCriteria, createdBy));

        // Published after transaction commits → picked up by async listener
        eventPublisher.publishEvent(new RequirementCreatedEvent(story.getId(), story.getExternalKey()));
        return story;
    }

    @Transactional(readOnly = true)
    public RequirementStory get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement story not found: " + id));
    }
}
