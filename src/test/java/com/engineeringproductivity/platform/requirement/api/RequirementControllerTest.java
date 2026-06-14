package com.engineeringproductivity.platform.requirement.api;

import com.engineeringproductivity.platform.requirement.domain.RequirementStoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequirementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequirementStoryRepository repository;

    @Test
    void createsRequirementStory() throws Exception {
        mockMvc.perform(post("/api/v1/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "JIRA-123",
                                  "title": "Create Customer API",
                                  "description": "Create customer management endpoints",
                                  "acceptanceCriteria": [
                                    "Email validation is mandatory",
                                    "Pagination is required",
                                    "Customer records use soft delete"
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalKey").value("JIRA-123"))
                .andExpect(jsonPath("$.status").value("ANALYSIS_PENDING"))
                .andExpect(jsonPath("$.acceptanceCriteria.length()").value(3));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRequirementStory() throws Exception {
        mockMvc.perform(post("/api/v1/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalKey": "",
                                  "title": "",
                                  "description": "",
                                  "acceptanceCriteria": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsDuplicateExternalKey() throws Exception {
        String request = """
                {
                  "externalKey": "JIRA-456",
                  "title": "Create Order API",
                  "description": "Create order management endpoints",
                  "acceptanceCriteria": ["Pagination is required"]
                }
                """;

        mockMvc.perform(post("/api/v1/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }
}
