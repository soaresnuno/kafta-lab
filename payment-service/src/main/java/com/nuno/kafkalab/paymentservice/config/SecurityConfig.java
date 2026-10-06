package com.nuno.kafkalab.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Resource server: cada pedido traz "Authorization: Bearer <JWT>" e o serviço valida a assinatura
    // com a chave pública do Keycloak (issuer-uri no application.properties). Sem token válido -> 401
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        // Quem chama os webhooks é o fornecedor, não um utilizador: não há JWT.
                        // A autenticação é a assinatura do webhook, validada pelo adapter do fornecedor
                        .requestMatchers("/payments/webhooks/**").permitAll()
                        // Página de pagamento do fornecedor falso (num fornecedor real é um site externo)
                        .requestMatchers("/fake-provider/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // Sem sessões: cada pedido é autenticado só pelo token
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protege cookies de sessão; com bearer tokens e webhooks não se aplica
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
