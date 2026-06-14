package com.engineeringproductivity.platform.search.document;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface GeneratedCodeSearchRepository
        extends ElasticsearchRepository<GeneratedCodeSearchDocument, String> {

    List<GeneratedCodeSearchDocument> findByContentContaining(String text);
    List<GeneratedCodeSearchDocument> findByRequirementId(String requirementId);
}
