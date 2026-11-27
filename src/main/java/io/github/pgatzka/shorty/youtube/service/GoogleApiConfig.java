package io.github.pgatzka.shorty.youtube.service;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** HTTP and JSON plumbing for the Google client, replaceable in tests. */
@Configuration(proxyBeanMethods = false)
class GoogleApiConfig {

    @Bean
    @ConditionalOnMissingBean
    HttpTransport googleHttpTransport() {
        return new NetHttpTransport();
    }

    @Bean
    @ConditionalOnMissingBean
    JsonFactory googleJsonFactory() {
        return GsonFactory.getDefaultInstance();
    }
}
