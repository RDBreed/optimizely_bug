package eu.phaf.optimizelybug;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
abstract class BaseOptimizelyTest {

    @Autowired
    private WireMockServer wireMockServer;
    @Autowired
    private OptimizelyFeatureToggle optimizelyFeatureToggle;

    @Test
    void shouldNotFail() {
        wireMockServer.setScenarioState("Optimizely dynamic", "Started");
        optimizelyFeatureToggle.checkFeatureToggle();
        assertThat(optimizelyFeatureToggle.getIsEnabled()).isTrue();
        // See wiremock mapping OR logs during test; Scenario goes from Started -> Disabled -> Unmodified
        await()
                .forever()
                .untilAsserted(() -> WireMock.verify(1, RequestPatternBuilder.newRequestPattern(RequestMethod.GET,
                        WireMock.urlPathEqualTo(
                                "/optimizely/FAKE_KEY.json"))));
        await()
                .forever()
                .untilAsserted(() -> WireMock.verify(2, RequestPatternBuilder.newRequestPattern(RequestMethod.GET,
                        WireMock.urlPathEqualTo(
                                "/optimizely/FAKE_KEY.json"))));
        await()
                .atMost(Duration.ofMinutes(2))
                .untilAsserted(() -> {
                    optimizelyFeatureToggle.checkFeatureToggle();
                    assertThat(optimizelyFeatureToggle.getIsEnabled()).isFalse();
                });

        await()
                .forever()
                .untilAsserted(() -> WireMock.verify(3, RequestPatternBuilder.newRequestPattern(RequestMethod.GET,
                        WireMock.urlPathEqualTo(
                                "/optimizely/FAKE_KEY.json"))));
        await()
                .atMost(Duration.ofMinutes(2))
                .untilAsserted(() -> {
                    optimizelyFeatureToggle.checkFeatureToggle();
                    assertThat(optimizelyFeatureToggle.getIsEnabled()).isFalse();
                });
    }
}
