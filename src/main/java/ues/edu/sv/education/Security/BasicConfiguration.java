package ues.edu.sv.education.Security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import ues.edu.sv.education.Security.AuthProviders.PasswordAuthProvider;
import ues.edu.sv.education.Security.filter.JwtFilter;
import ues.edu.sv.education.service.auth.CustomUserDetailService;
import ues.edu.sv.education.service.auth.UserAuthService;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class BasicConfiguration {
    private final CustomUserDetailService userService;
    private final JwtFilter jwtFilter;

    @Bean
    public UserDetailsService userDetailsService() {
        return userService;
    }

    @Bean
    public AuthenticationManager authentificationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            PasswordEncoder encoder,
            UserAuthService userAuthService) {

        return new PasswordAuthProvider(
                encoder,
                userAuthService
        );
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Education API")
                        .version("1.0")
                        .description("API para la gestión del sistema Education"))
                .components(new Components()
                        .addSecuritySchemes(
                                "jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .cors(c -> c.configurationSource(request -> {
                    var corses = new CorsConfiguration();
                    corses.setAllowCredentials(true);
                    corses.addAllowedOriginPattern("*"); // o tu frontend específico
                    corses.addAllowedHeader("*");
                    corses.addAllowedMethod("*");
                    corses.setMaxAge(3600L);

                    return corses;
                }))
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                16,     // salt length
                32,     // hash length
                1,      // parallelism
                65536,  // memory (64 MB)
                3 // iterations
        );
    }

}
