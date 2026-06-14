package com.engineeringproductivity.platform.search;

import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.engineeringproductivity.platform.search.document.GeneratedCodeSearchDocument;
import com.engineeringproductivity.platform.search.document.GeneratedCodeSearchRepository;
import com.engineeringproductivity.platform.search.document.RequirementSearchDocument;
import com.engineeringproductivity.platform.search.document.RequirementSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true")
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final RequirementSearchRepository requirementRepo;
    private final GeneratedCodeSearchRepository codeRepo;
    private final EmbeddingService embeddingService;
    private final ElasticsearchOperations esOperations;

    public SearchService(RequirementSearchRepository requirementRepo,
                          GeneratedCodeSearchRepository codeRepo,
                          EmbeddingService embeddingService,
                          ElasticsearchOperations esOperations) {
        this.requirementRepo = requirementRepo;
        this.codeRepo = codeRepo;
        this.embeddingService = embeddingService;
        this.esOperations = esOperations;
    }

    /**
     * Searches requirements and generated code.
     * Uses semantic (vector) search if Gemini API key is configured, otherwise keyword search.
     */
    public SearchResult search(String queryText) {
        try {
            float[] embedding = embeddingService.embed(queryText);
            if (embedding.length > 0) {
                return vectorSearch(queryText, embedding);
            }
        } catch (Exception e) {
            log.warn("Embedding failed for '{}', using keyword search: {}", queryText, e.getMessage());
        }
        return keywordSearch(queryText);
    }

    private SearchResult vectorSearch(String queryText, float[] embedding) {
        try {
            List<Float> queryVector = new ArrayList<>(embedding.length);
            for (float v : embedding) queryVector.add(v);

            KnnSearch knnSearch = KnnSearch.of(k -> k
                    .field("embedding")
                    .queryVector(queryVector)
                    .k(10)
                    .numCandidates(50));

            NativeQuery query = NativeQuery.builder()
                    .withKnnSearches(knnSearch)
                    .build();

            List<RequirementSearchDocument> requirements = esOperations
                    .search(query, RequirementSearchDocument.class)
                    .getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .toList();

            List<GeneratedCodeSearchDocument> code = esOperations
                    .search(query, GeneratedCodeSearchDocument.class)
                    .getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .toList();

            log.info("Vector search for '{}' → {} requirements, {} code files",
                    queryText, requirements.size(), code.size());
            return new SearchResult(requirements, code, requirements.size() + code.size());

        } catch (Exception e) {
            log.warn("Vector search failed ({}), falling back to keyword search", e.getMessage());
            return keywordSearch(queryText);
        }
    }

    /**
     * Keyword search using a native multi_match query.
     * Avoids Spring Data derived queries which break on multi-word input.
     * Falls back to empty results on any ES error — never throws.
     */
    private SearchResult keywordSearch(String queryText) {
        try {
            // multi_match across all relevant text fields with OR logic
            Query multiMatch = Query.of(q -> q.multiMatch(mm -> mm
                    .query(queryText)
                    .fields("title^3", "description^2", "acceptanceCriteria",
                            "entityNames", "endpointPaths", "validationFields",
                            "securityCategories", "externalKey")
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or)
                    .fuzziness("AUTO")
            ));

            NativeQuery reqQuery = NativeQuery.builder()
                    .withQuery(multiMatch)
                    .withMaxResults(20)
                    .build();

            List<RequirementSearchDocument> requirements = esOperations
                    .search(reqQuery, RequirementSearchDocument.class)
                    .getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .toList();

            // For code: search in content field
            Query codeMatch = Query.of(q -> q.multiMatch(mm -> mm
                    .query(queryText)
                    .fields("content^2", "fileName", "fileType", "externalKey")
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or)
                    .fuzziness("AUTO")
            ));

            NativeQuery codeQuery = NativeQuery.builder()
                    .withQuery(codeMatch)
                    .withMaxResults(20)
                    .build();

            List<GeneratedCodeSearchDocument> code = esOperations
                    .search(codeQuery, GeneratedCodeSearchDocument.class)
                    .getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .toList();

            log.info("Keyword search for '{}' → {} requirements, {} code files",
                    queryText, requirements.size(), code.size());
            return new SearchResult(requirements, code, requirements.size() + code.size());

        } catch (Exception e) {
            log.error("Keyword search failed for '{}': {}", queryText, e.getMessage());
            return new SearchResult(List.of(), List.of(), 0);
        }
    }

    public record SearchResult(
            List<RequirementSearchDocument> requirements,
            List<GeneratedCodeSearchDocument> generatedCode,
            int total
    ) {}
}
