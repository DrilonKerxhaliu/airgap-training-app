package it.egeos.cut3g.airgap.security;

import org.jasig.cas.client.session.SingleSignOutFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.Collections;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Value("${cas.server-url}")
    private String casServerUrl;

    @Value("${cas.logout-url}")
    private String casLogoutUrl;

    @Autowired
    private ServiceProperties serviceProperties;

    @Autowired
    private CasAuthenticationProvider casAuthenticationProvider;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(casAuthenticationProvider));
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        CasAuthenticationEntryPoint entryPoint = new CasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(casServerUrl + "/login");
        entryPoint.setServiceProperties(serviceProperties);
        return entryPoint;
    }

    // ✅ SUCCESS HANDLER (IMPORTANT)
    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setAlwaysUseDefaultTargetUrl(false);
        handler.setDefaultTargetUrl("/airgap"); // fallback

        return handler;
    }

    // ✅ CAS FILTER
    @Bean
    public CasAuthenticationFilter casAuthenticationFilter() throws Exception {
        CasAuthenticationFilter filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager());
        filter.setFilterProcessesUrl("/login/cas");
        filter.setServiceProperties(serviceProperties);
        filter.setAuthenticationSuccessHandler(successHandler());

        return filter;
    }

    @Bean
    public LogoutFilter logoutFilter() {
        String logoutRedirect = casLogoutUrl + "?service=http://172.30.150.98:8081/airgap";
        LogoutFilter logoutFilter = new LogoutFilter(logoutRedirect, new SecurityContextLogoutHandler());
        logoutFilter.setFilterProcessesUrl("/logout");
        return logoutFilter;
    }

    @Bean
    public SingleSignOutFilter singleSignOutFilter() {
        return new SingleSignOutFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .headers()
                .frameOptions().sameOrigin()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint())
                .and()
                .requestCache()
                .requestCache(new HttpSessionRequestCache())
                .and()
                .addFilterBefore(singleSignOutFilter(), CasAuthenticationFilter.class)
                .addFilterBefore(logoutFilter(), CasAuthenticationFilter.class)
                .addFilterBefore(casAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests()
                .antMatchers("/login/**", "/error", "/actuator/**").permitAll()
                .antMatchers("/airgap/**").hasRole("ADMIN")
                .anyRequest().authenticated();

        return http.build();
    }
}
