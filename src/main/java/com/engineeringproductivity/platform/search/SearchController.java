package com.engineeringproductivity.platform.search;

import com.engineeringproductivity.platform.search.document.GeneratedCodeSearchRepository;
import com.engineeringproductivity.platform.search.document.RequirementSearchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true")
public class SearchController {

    private final SearchService searchService;
    private final RequirementSearchRepository requirementRepo;
    private final GeneratedCodeSearchRepository codeRepo;

    public SearchController(SearchService searchService,
                             RequirementSearchRepository requirementRepo,
                             GeneratedCodeSearchRepository codeRepo) {
        this.searchService = searchService;
        this.requirementRepo = requirementRepo;
        this.codeRepo = codeRepo;
    }

    @GetMapping
    public SearchService.SearchResult search(@RequestParam String q) {
        return searchService.search(q);
    }

    /** Index stats for the dashboard ES status bar. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "requirementDocs", requirementRepo.count(),
                "codeDocs",        codeRepo.count(),
                "status",          "live"
        );
    }
}
