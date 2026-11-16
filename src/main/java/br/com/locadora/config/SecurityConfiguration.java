package br.com.locadora.config;

import static org.springframework.security.config.Customizer.withDefaults;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperatorProperties.class)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService operator(OperatorProperties properties, PasswordEncoder encoder) {
        var operator = User.withUsername(properties.username())
                .password(encoder.encode(properties.password()))
                .roles("OPERATOR")
                .build();
        return new InMemoryUserDetailsManager(operator);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // HTTP Basic pode ser enviado automaticamente por navegadores.
        // Preservar CSRF; stateless, por si só, não elimina esse risco.
        return http
                .authorizeHttpRequests(authorize -> authorize
                        // Preserva o status original ao renderizar um erro do servlet.
                        // Acesso direto a /error continua exigindo autenticação.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasRole("OPERATOR"))
                .httpBasic(withDefaults())
                // Basic autentica cada chamada. Não executar estratégias de novo
                // login nem armazenar autenticação na sessão usada pelo CSRF.
                .sessionManagement(session -> session.disable())
                .securityContext(context -> context.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}
