package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ai.metadata")
public record MetadataProperties(boolean enabled, String model, String baseUrl, Duration connectTimeout,
        Duration readTimeout, int maxOutputTokens, int maxContentChars, AiQuotaProperties quota) {
    public MetadataProperties {
        if (enabled) {
            URI uri;
            try { uri=URI.create(baseUrl == null ? "" : baseUrl); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("Metadata base URL is invalid"); }
            if (!java.util.Set.of("http","https").contains(uri.getScheme()==null ? "" : uri.getScheme())
                    || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("Metadata base URL is invalid");
            if (model==null || model.isBlank() || model.length()>255 || quota==null)
                throw new IllegalArgumentException("Metadata model/quota is invalid");
            if (connectTimeout==null || readTimeout==null || connectTimeout.toMillis()<1 || readTimeout.toMillis()<1
                    || connectTimeout.toMillis()>Integer.MAX_VALUE || readTimeout.toMillis()>Integer.MAX_VALUE
                    || maxOutputTokens<1 || maxOutputTokens>8192 || maxContentChars<1 || maxContentChars>32000)
                throw new IllegalArgumentException("Metadata limits are invalid");
        }
    }
    @Override public String toString() { return "MetadataProperties[enabled="+enabled+"]"; }
}
