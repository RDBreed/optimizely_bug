package com.optimizely.ab.config;

import com.optimizely.ab.OptimizelyHttpClient;
import com.optimizely.ab.annotations.VisibleForTesting;
import com.optimizely.ab.config.parser.ConfigParseException;
import com.optimizely.ab.internal.PropertyUtils;
import com.optimizely.ab.notification.NotificationCenter;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;


//  Created this because there is a bug in the SDK: https://github.com/optimizely/java-sdk/issues/526
@SuppressWarnings("PMD")
public class BugFixHttpProjectConfigManager extends PollingProjectConfigManager {

    public static final String CONFIG_POLLING_DURATION = "http.project.config.manager.polling.duration";
    public static final String CONFIG_POLLING_UNIT = "http.project.config.manager.polling.unit";
    public static final String CONFIG_BLOCKING_DURATION = "http.project.config.manager.blocking.duration";
    public static final String CONFIG_BLOCKING_UNIT = "http.project.config.manager.blocking.unit";
    public static final String CONFIG_EVICT_DURATION = "http.project.config.manager.evict.duration";
    public static final String CONFIG_EVICT_UNIT = "http.project.config.manager.evict.unit";
    public static final String CONFIG_SDK_KEY = "http.project.config.manager.sdk.key";
    public static final String CONFIG_DATAFILE_AUTH_TOKEN = "http.project.config.manager.datafile.auth.token";

    public static final long DEFAULT_POLLING_DURATION = 5;
    public static final TimeUnit DEFAULT_POLLING_UNIT = TimeUnit.MINUTES;
    public static final long DEFAULT_BLOCKING_DURATION = 10;
    public static final TimeUnit DEFAULT_BLOCKING_UNIT = TimeUnit.SECONDS;
    public static final long DEFAULT_EVICT_DURATION = 1;
    public static final TimeUnit DEFAULT_EVICT_UNIT = TimeUnit.MINUTES;

    private static final Logger LOGGER = LoggerFactory.getLogger(BugFixHttpProjectConfigManager.class);

    private final OptimizelyHttpClient httpClient;
    private final URI uri;
    private final String datafileAccessToken;
    private String datafileLastModified;

    private BugFixHttpProjectConfigManager(long period,
                                           TimeUnit timeUnit,
                                           OptimizelyHttpClient httpClient,
                                           String url,
                                           String datafileAccessToken,
                                           long blockingTimeoutPeriod,
                                           TimeUnit blockingTimeoutUnit,
                                           NotificationCenter notificationCenter) {
        super(period, timeUnit, blockingTimeoutPeriod, blockingTimeoutUnit, notificationCenter);
        this.httpClient = httpClient;
        this.uri = URI.create(url);
        this.datafileAccessToken = datafileAccessToken;
    }

    public URI getUri() {
        return uri;
    }

    public String getLastModified() {
        return datafileLastModified;
    }

    public String getDatafileFromResponse(HttpResponse response) throws NullPointerException, IOException {
        StatusLine statusLine = response.getStatusLine();

        if (statusLine == null) {
            throw new ClientProtocolException("unexpected response from event endpoint, status is null");
        }

        int status = statusLine.getStatusCode();

        // Datafile has not updated
        if (status == HttpStatus.SC_NOT_MODIFIED) {
            LOGGER.debug("Not updating ProjectConfig as datafile has not updated since " + datafileLastModified);
            return null;
        }

        if (status >= 200 && status < 300) {
            // read the response, so we can close the connection
            HttpEntity entity = response.getEntity();
            Header lastModifiedHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
            if (lastModifiedHeader != null) {
                datafileLastModified = lastModifiedHeader.getValue();
            }
            return EntityUtils.toString(entity, "UTF-8");
        } else {
            throw new ClientProtocolException("unexpected response when trying to fetch datafile, status: " + status);
        }
    }

    static ProjectConfig parseProjectConfig(String datafile) throws ConfigParseException {
        return new DatafileProjectConfig.Builder().withDatafile(datafile).build();
    }

    @Override
    public ProjectConfig getConfig() {
        var config = super.getConfig();
        setConfig(config);
        return config;
    }

    @Override
    protected ProjectConfig poll() {
        HttpGet httpGet = createHttpRequest();
        CloseableHttpResponse response = null;
        LOGGER.debug("Fetching datafile from: {}", httpGet.getURI());
        try {
            response = httpClient.execute(httpGet);
            String datafile = getDatafileFromResponse(response);
            if (datafile == null) {
                return null;
            }
            return parseProjectConfig(datafile);
        } catch (ConfigParseException | IOException e) {
            LOGGER.error("Error fetching datafile", e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    LOGGER.warn(e.getLocalizedMessage());
                }
            }
        }

        return null;
    }

    @VisibleForTesting
    HttpGet createHttpRequest() {
        HttpGet httpGet = new HttpGet(uri);

        if (datafileAccessToken != null) {
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + datafileAccessToken);
        }

        if (datafileLastModified != null) {
            httpGet.setHeader(HttpHeaders.IF_MODIFIED_SINCE, datafileLastModified);
        }

        return httpGet;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String datafile;
        private String url;
        private String datafileAccessToken = null;
        private String format = "https://cdn.optimizely.com/datafiles/%s.json;";
        private String authFormat = "https://config.optimizely.com/datafiles/auth/%s.json;";
        private OptimizelyHttpClient httpClient;
        private NotificationCenter notificationCenter;

        String sdkKey = PropertyUtils.get(CONFIG_SDK_KEY);
        long period = PropertyUtils.getLong(CONFIG_POLLING_DURATION, DEFAULT_POLLING_DURATION);
        TimeUnit timeUnit = PropertyUtils.getEnum(CONFIG_POLLING_UNIT, TimeUnit.class, DEFAULT_POLLING_UNIT);

        long blockingTimeoutPeriod = PropertyUtils.getLong(CONFIG_BLOCKING_DURATION, DEFAULT_BLOCKING_DURATION);
        TimeUnit blockingTimeoutUnit = PropertyUtils.getEnum(CONFIG_BLOCKING_UNIT, TimeUnit.class, DEFAULT_BLOCKING_UNIT);

        // force-close the persistent connection after this idle time
        long evictConnectionIdleTimePeriod = PropertyUtils.getLong(CONFIG_EVICT_DURATION, DEFAULT_EVICT_DURATION);
        TimeUnit evictConnectionIdleTimeUnit = PropertyUtils.getEnum(CONFIG_EVICT_UNIT, TimeUnit.class, DEFAULT_EVICT_UNIT);

        public Builder withDatafile(String datafile) {
            this.datafile = datafile;
            return this;
        }

        public Builder withSdkKey(String sdkKey) {
            this.sdkKey = sdkKey;
            return this;
        }

        public Builder withDatafileAccessToken(String token) {
            this.datafileAccessToken = token;
            return this;
        }

        public Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder withFormat(String format) {
            this.format = format;
            return this;
        }

        public Builder withOptimizelyHttpClient(OptimizelyHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Makes HttpClient proactively evict idle connections from theœ
         * connection pool using a background thread.
         *
         * @param maxIdleTime maximum time persistent connections can stay idle while kept alive
         * in the connection pool. Connections whose inactivity period exceeds this value will
         * get closed and evicted from the pool.  Set to 0 to disable eviction.
         * @param maxIdleTimeUnit time unit for the above parameter.
         * @return A HttpProjectConfigManager builder
         * @see org.apache.http.impl.client.HttpClientBuilder#evictIdleConnections(long, TimeUnit)
         */
        public Builder withEvictIdleConnections(long maxIdleTime, TimeUnit maxIdleTimeUnit) {
            this.evictConnectionIdleTimePeriod = maxIdleTime;
            this.evictConnectionIdleTimeUnit = maxIdleTimeUnit;
            return this;
        }

        /**
         * Configure time to block before Completing the future. This timeout is used on the first call
         * to {@link PollingProjectConfigManager#getConfig()}. If the timeout is exceeded then the
         * PollingProjectConfigManager will begin returning null immediately until the call to Poll
         * succeeds.
         *
         * @param period A timeout period
         * @param timeUnit A timeout unit
         * @return A HttpProjectConfigManager builder
         */
        public Builder withBlockingTimeout(Long period, TimeUnit timeUnit) {
            if (timeUnit == null) {
                LOGGER.warn("TimeUnit cannot be null. Keeping default period: {} and time unit: {}", this.blockingTimeoutPeriod, this.blockingTimeoutUnit);
                return this;
            }

            if (period == null) {
                LOGGER.warn("Timeout cannot be null. Keeping default period: {} and time unit: {}", this.blockingTimeoutPeriod, this.blockingTimeoutUnit);
                return this;
            }

            if (period <= 0) {
                LOGGER.warn("Timeout cannot be <= 0. Keeping default period: {} and time unit: {}", this.blockingTimeoutPeriod, this.blockingTimeoutUnit);
                return this;
            }

            this.blockingTimeoutPeriod = period;
            this.blockingTimeoutUnit = timeUnit;

            return this;
        }

        public Builder withPollingInterval(Long period, TimeUnit timeUnit) {
            if (timeUnit == null) {
                LOGGER.warn("TimeUnit cannot be null. Keeping default period: {} and time unit: {}", this.period, this.timeUnit);
                return this;
            }

            if (period == null) {
                LOGGER.warn("Interval cannot be null. Keeping default period: {} and time unit: {}", this.period, this.timeUnit);
                return this;
            }

            if (period <= 0) {
                LOGGER.warn("Interval cannot be <= 0. Keeping default period: {} and time unit: {}", this.period, this.timeUnit);
                return this;
            }

            this.period = period;
            this.timeUnit = timeUnit;

            return this;
        }

        public Builder withNotificationCenter(NotificationCenter notificationCenter) {
            this.notificationCenter = notificationCenter;
            return this;
        }

        /**
         * HttpProjectConfigManager.Builder that builds and starts a HttpProjectConfigManager.
         * This is the default builder which will block until a config is available.
         *
         * @return {@link BugFixHttpProjectConfigManager}
         */
        public BugFixHttpProjectConfigManager build() {
            return build(false);
        }

        /**
         * HttpProjectConfigManager.Builder that builds and starts a HttpProjectConfigManager.
         *
         * @param defer When true, we will not wait for the configuration to be available
         * before returning the HttpProjectConfigManager instance.
         * @return {@link BugFixHttpProjectConfigManager}
         */
        public BugFixHttpProjectConfigManager build(boolean defer) {
            if (period <= 0) {
                LOGGER.warn("Invalid polling interval {}, {}. Defaulting to {}, {}",
                        period, timeUnit, DEFAULT_POLLING_DURATION, DEFAULT_POLLING_UNIT);
                period = DEFAULT_POLLING_DURATION;
                timeUnit = DEFAULT_POLLING_UNIT;
            }

            if (blockingTimeoutPeriod <= 0) {
                LOGGER.warn("Invalid polling interval {}, {}. Defaulting to {}, {}",
                        blockingTimeoutPeriod, blockingTimeoutUnit, DEFAULT_BLOCKING_DURATION, DEFAULT_BLOCKING_UNIT);
                blockingTimeoutPeriod = DEFAULT_BLOCKING_DURATION;
                blockingTimeoutUnit = DEFAULT_BLOCKING_UNIT;
            }

            if (httpClient == null) {
                httpClient = OptimizelyHttpClient.builder()
                        .withEvictIdleConnections(evictConnectionIdleTimePeriod, evictConnectionIdleTimeUnit)
                        .build();
            }

            if (url == null) {
                if (sdkKey == null) {
                    throw new NullPointerException("sdkKey cannot be null");
                }

                if (datafileAccessToken == null) {
                    url = String.format(format, sdkKey);
                } else {
                    url = String.format(authFormat, sdkKey);
                }
            }

            if (notificationCenter == null) {
                notificationCenter = new NotificationCenter();
            }

            BugFixHttpProjectConfigManager httpProjectManager = new BugFixHttpProjectConfigManager(
                    period,
                    timeUnit,
                    httpClient,
                    url,
                    datafileAccessToken,
                    blockingTimeoutPeriod,
                    blockingTimeoutUnit,
                    notificationCenter);

            if (datafile != null) {
                try {
                    ProjectConfig projectConfig = BugFixHttpProjectConfigManager.parseProjectConfig(datafile);
                    httpProjectManager.setConfig(projectConfig);
                } catch (ConfigParseException e) {
                    LOGGER.warn("Error parsing fallback datafile.", e);
                }
            }

            httpProjectManager.start();

            // Optionally block until config is available.
            if (!defer) {
                httpProjectManager.getConfig();
            }

            return httpProjectManager;
        }
    }
}