package com.rotalog.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuração do RestTemplate usado para chamar api-notificacoes a partir dos
 * alertas de manutenção preventiva.
 *
 * Diferente de {@code NotificacaoClient}/{@code EntregaClient} (que instanciam o
 * RestTemplate manualmente), aqui ele é exposto como Bean para permitir mock
 * via {@code MockRestServiceServer} em testes unitários.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate notificacaoRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
