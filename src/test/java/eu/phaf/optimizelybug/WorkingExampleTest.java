package eu.phaf.optimizelybug;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("working")
class WorkingExampleTest {

    @Autowired
    private WireMockServer wireMockServer;
    @Autowired
    private OptimizelyFeatureToggle optimizelyFeatureToggle;

    @Test
    void shouldNotFail() {
        wireMockServer.setScenarioState("Optimizely dynamic", "Started");
        optimizelyFeatureToggle.checkFeatureToggle();
        assertThat(optimizelyFeatureToggle.getIsEnabled()).isTrue();
        // See wiremock mapping OR logs during test; Scenario goes from Started -> Started2 -> Disabled -> Unmodified
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    optimizelyFeatureToggle.checkFeatureToggle();
                    assertThat(optimizelyFeatureToggle.getIsEnabled()).isFalse();
                });
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    optimizelyFeatureToggle.checkFeatureToggle();
                    assertThat(optimizelyFeatureToggle.getIsEnabled()).isFalse();
                });
    }
}
