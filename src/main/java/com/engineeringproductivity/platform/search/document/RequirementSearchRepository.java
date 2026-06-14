package com.engineeringproductivity.platform.search.document;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface RequirementSearchRepository
        extends ElasticsearchRepository<RequirementSearchDocument, String> {

    List<RequirementSearchDocument> findByTitleContainingOrDescriptionContaining(
            String title, String description);

    List<RequirementSearchDocument> findByEntityNamesContaining(String entityName);
}
