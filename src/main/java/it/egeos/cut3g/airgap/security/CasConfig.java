package it.egeos.cut3g.airgap.security;

import org.jasig.cas.client.validation.Cas30ServiceTicketValidator;
import org.jasig.cas.client.validation.TicketValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

@Configuration
public class CasConfig {

    @Value("${cas.validate-url}")
    private String casValidateUrl;

    @Value("${cas.redirect-uri}")
    private String redirectUri;

    @Bean
    public ServiceProperties serviceProperties() {
        ServiceProperties sp = new ServiceProperties();
        sp.setService(redirectUri);
        sp.setSendRenew(false);
        return sp;
    }

    @Bean
    public TicketValidator ticketValidator() {
        return new Cas30ServiceTicketValidator(casValidateUrl);
    }

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider(
            TicketValidator ticketValidator,
            ServiceProperties serviceProperties) {

        CasAuthenticationProvider provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties);
        provider.setTicketValidator(ticketValidator);

        provider.setUserDetailsService(
                username -> new User(
                        username,
                        "N/A",
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")
                )
        );

        provider.setKey("CAS_PROVIDER");
        return provider;
    }
}