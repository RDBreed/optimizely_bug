package eu.phaf.optimizelybug;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyUserContext;
import com.optimizely.ab.optimizelydecision.OptimizelyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OptimizelyFeatureToggle {
    private static AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final static Logger LOG = LoggerFactory.getLogger(OptimizelyFeatureToggle.class);
    private final ObjectProvider<Optimizely> optimizely;

    public OptimizelyFeatureToggle(ObjectProvider<Optimizely> optimizely) {
        this.optimizely = optimizely;
    }

    public void checkFeatureToggle() {
        isEnabled.set(isEnabled("targetfilereceiver"));
    }

    public boolean getIsEnabled() {
        return isEnabled.get();
    }

    private boolean isEnabled(String featureName) {
        final OptimizelyUserContext userContext = Optional.ofNullable(optimizely.getIfAvailable())
                .orElseThrow(() -> new RuntimeException("Optimizely Bean not found"))
                .createUserContext(UUID.randomUUID().toString(), Map.of());
        final OptimizelyDecision decision = userContext.decide(featureName);
        if (decision.getVariationKey() == null) {
            LOG.error("Feature flag {} in Optimizely gave errors: {}", featureName, decision.getReasons());
        }
        return decision.getEnabled();
    }
}
