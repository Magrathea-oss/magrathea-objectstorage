package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.admin.web.AdminRouter;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp5OperabilitySteps {

    private final WebTestClient adminClient;
    private WebTestClient activeClient;
    private EntityExchangeResult<String> response;

    public PhaseEp5OperabilitySteps(@Qualifier("adminWebTestClient") WebTestClient adminClient) {
        this.adminClient = adminClient;
        this.activeClient = adminClient;
    }

    @Given("the Admin API is configured with storage policy, storage device, and disk-set catalogs")
    public void adminApiIsConfiguredWithCatalogs() {
        activeClient = adminClient;
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

    @Given("the Admin API is missing storage policy, storage device, and disk-set catalogs")
    public void adminApiIsMissingStorageCatalogs() {
        AdminRouter router = new AdminRouter(
            emptyProvider(StoragePolicyCatalog.class),
            emptyProvider(StorageDeviceCatalog.class),
            emptyProvider(DiskSetCatalog.class));
        activeClient = WebTestClient.bindToRouterFunction(router.adminRoutes()).build();
    }

    @When("an Admin API client requests GET {string}")
    public void adminApiClientRequestsGet(String path) {
        response = activeClient.get().uri(path)
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
            assertReadinessComponentStatus(component, "ready");
        }
    }

    @Then("the Admin API readiness components have status:")
    public void adminApiReadinessComponentsHaveStatus(DataTable table) {
        table.asMaps().forEach(row -> assertReadinessComponentStatus(row.get("component"), row.get("status")));
    }

    private void assertReadinessComponentStatus(String component, String expectedStatus) {
        List<String> statuses = JsonPath.read(responseBody(), "$.components[?(@.name == '" + component + "')].status");
        assertThat(statuses)
            .as("readiness component %s", component)
            .containsExactly(expectedStatus);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }

    private String responseBody() {
        assertThat(response).as("Admin API response must be captured").isNotNull();
        assertThat(response.getResponseBody()).as("Admin API response body").isNotNull();
        return response.getResponseBody();
    }
}
