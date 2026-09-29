package com.example.raisetime.config;

import com.example.raisetime.security.AuthUserJwtAuthenticationConverter;
import com.example.raisetime.security.CookieBearerTokenResolver;
import com.example.raisetime.security.JsonAccessDeniedHandler;
import com.example.raisetime.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

// 認証は JWT（アクセストークン）を access_token Cookie で受け取る方式。サーバーはセッションを持たない
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ログインしなくても呼べる API（それ以外はすべてログイン必須）
    private static final String[] PUBLIC_GET = {"/api/auth/csrf", "/actuator/health"};
    private static final String[] PUBLIC_POST = {
        "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CsrfTokenRepository csrfTokenRepository,
                                                   JwtDecoder jwtDecoder,
                                                   AuthUserJwtAuthenticationConverter jwtAuthenticationConverter,
                                                   CookieBearerTokenResolver bearerTokenResolver,
                                                   JsonAuthenticationEntryPoint authenticationEntryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // セッションは使わない（ログイン状態はリクエストごとに JWT で確認する）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Cookie で認証するため CSRF 対策は必要。トークンを XSRF-TOKEN Cookie（JavaScript から読める）で渡し、
                // POST などでは X-XSRF-TOKEN ヘッダーで送り返してもらう
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Spring Security は「認証に成功したら CSRF トークンを消す」のが標準だが、
                        // JWT ではリクエストのたびに認証するため、そのままだと毎回トークンが消えて次の POST が 403 になる。
                        // ログイン時のトークンの入れ替えは AuthTokenIssuer で行うので、ここでは消さない
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                // access_token Cookie の JWT を検証し、ログイン中のユーザー（AuthUser）にする
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // ログイン・ログアウトは AuthController の API で行うため、Spring Security の画面・Basic 認証・ログアウト機能は使わない
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ログイン（メールアドレスとパスワードの照合）に使う
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").path("/"));
        return repository;
    }
}
