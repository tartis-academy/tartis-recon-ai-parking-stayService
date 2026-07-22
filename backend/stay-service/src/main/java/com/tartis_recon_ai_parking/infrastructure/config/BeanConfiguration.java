package com.tartis_recon_ai_parking.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfiguration {

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        
        // Timeout de conexión: tiempo máximo para conectar con el microservicio (3 segundos)
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        
        // Timeout de lectura: tiempo máximo esperando la respuesta (5 segundos)
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}