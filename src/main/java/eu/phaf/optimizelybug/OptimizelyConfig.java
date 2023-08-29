package eu.phaf.optimizelybug;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyFactory;
import com.optimizely.ab.config.BugFixHttpProjectConfigManager;
import com.optimizely.ab.config.HttpProjectConfigManager;
import com.optimizely.ab.config.PollingProjectConfigManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

@Configuration
public class OptimizelyConfig {
    @Bean
    @Profile("working")
    public Optimizely optimizely(@Value("${optimizely.key}") final String optimizelyKey,
                                 @Value("${optimizely.base-url}") final String optimizelyBasePath) {
        final BugFixHttpProjectConfigManager httpProjectConfigManager = BugFixHttpProjectConfigManager.builder()
                .withSdkKey(optimizelyKey)
                .withFormat(optimizelyBasePath + "%s.json")
                .withPollingInterval(1L, TimeUnit.MINUTES)
                .build();
        return OptimizelyFactory.newDefaultInstance(httpProjectConfigManager);
    }

    @Bean
    @Profile("notworking")
    public Optimizely optimizelyNotWorking(@Value("${optimizely.key}") final String optimizelyKey,
                                           @Value("${optimizely.base-url}") final String optimizelyBasePath) {
        PollingProjectConfigManager httpProjectConfigManager = HttpProjectConfigManager.builder()
                .withSdkKey(optimizelyKey)
                .withFormat(optimizelyBasePath + "%s.json")
                .withPollingInterval(1L, TimeUnit.MINUTES)
                .build();
        return OptimizelyFactory.newDefaultInstance(httpProjectConfigManager);
    }
}
