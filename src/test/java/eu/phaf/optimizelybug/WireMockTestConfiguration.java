package eu.phaf.optimizelybug;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import org.springframework.cloud.contract.wiremock.WireMockConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WireMockTestConfiguration {
    @Bean
    WireMockConfigurationCustomizer optionsCustomizer(){
        return config -> config.notifier(new ConsoleNotifier(true));
    }
}
