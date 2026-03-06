package ues.edu.sv.education.Security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ues.edu.sv.education.service.auth.CustomUserDetailService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor

public class BasicConfiguration {
    private final CustomUserDetailService userService;


    @Bean
    public UserDetailsService userDetailsService(){
        return userService;
    }

    @Bean
    public AuthenticationManager authentificationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean
    public AuthenticationProvider authentificationProvider() throws Exception {
        DaoAuthenticationProvider authProv=new DaoAuthenticationProvider(userDetailsService());
        authProv.setPasswordEncoder(passwordEncoder());
        return authProv;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request.anyRequest().permitAll()
                )
                .formLogin(AbstractHttpConfigurer::disable)
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
