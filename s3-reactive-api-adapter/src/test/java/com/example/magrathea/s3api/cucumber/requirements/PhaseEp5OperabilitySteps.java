package com.example.magrathea.s3api.cucumber.requirements;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5OperabilitySteps {

    private final WebTestClient adminClient;
    private EntityExchangeResult<String> response;

    public PhaseEp5OperabilitySteps(@Qualifier("adminWebTestClient") WebTestClient adminClient) {
        this.adminClient = adminClient;
    }

    @Given("the Admin API is configured with storage policy, storage device, and disk-set catalogs")
    public void adminApiIsConfiguredWithCatalogs() {
        adminClient.get().uri("/admin/storage-policies")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.storagePolicies[0].storageClassId").exists();
        adminClient.get().uri("/admin/storage-devices")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.storageDevices[0].id").exists();
        adminClient.get().uri("/admin/disk-sets")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.diskSets[0].name").exists();
    }

    @When("an Admin API client requests GET {string}")
    public void adminApiClientRequestsGet(String path) {
        response = adminClient.get().uri(path)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @Then("the Admin API response status is {int}")
    public void adminApiResponseStatusIs(int expectedStatus) {
        assertThat(response).as("Admin API response must be captured").isNotNull();
        HttpStatusCode status = response.getStatus();
        assertThat(status.value()).isEqualTo(expectedStatus);
    }

    @Then("the Admin API response field {string} is {string}")
    public void adminApiResponseFieldIs(String field, String expectedValue) {
        assertThat(responseBody()).isNotBlank();
        String actual = JsonPath.read(responseBody(), "$." + field);
        assertThat(actual).isEqualTo(expectedValue);
    }

    @Then("the Admin API response has a link named {string} to {string}")
    public void adminApiResponseHasLinkNamedTo(String linkName, String href) {
        String actual = JsonPath.read(responseBody(), "$._links." + linkName + ".href");
        assertThat(actual).isEqualTo(href);
    }

    @Then("the Admin API readiness components are ready:")
    public void adminApiReadinessComponentsAreReady(DataTable table) {
        for (String component : table.asMaps().stream().map(row -> row.get("component")).toList()) {
            List<String> statuses = JsonPath.read(responseBody(), "$.components[?(@.name == '" + component + "')].status");
            assertThat(statuses)
                .as("readiness component %s", component)
                .containsExactly("ready");
        }
    }

    private String responseBody() {
        assertThat(response).as("Admin API response must be captured").isNotNull();
        assertThat(response.getResponseBody()).as("Admin API response body").isNotNull();
        return response.getResponseBody();
    }
}
