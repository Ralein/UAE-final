package com.yoursp.uaepass.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the {@code uaepass.*} YAML properties into a typed bean.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "uaepass")
public class UaePassProperties {

    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String redirectUri;
    private String scope;
    private String acrValues;
    private String uiLocales;

    /** Convenience: authorization endpoint */
    public String getAuthorizeUrl() {
        return baseUrl + "/idshub/authorize";
    }

    /** Convenience: token endpoint */
    public String getTokenUrl() {
        return baseUrl + "/idshub/token";
    }

    /** Convenience: userinfo endpoint */
    public String getUserInfoUrl() {
        return baseUrl + "/idshub/userinfo";
    }

    /** Convenience: logout endpoint */
    public String getLogoutUrl() {
        return baseUrl + "/idshub/logout";
    }

    /** Convenience: introspect endpoint */
    public String getIntrospectUrl() {
        return baseUrl + "/idshub/introspect";
    }
}
