package vn.tsolve.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.tsolve.common.security.SecurityProblemWriter;
import vn.tsolve.common.tenant.ThreadLocalTenantContext;
import vn.tsolve.platform.service.WorkspaceCatalog;
import vn.tsolve.platform.web.HostClassifier;
import vn.tsolve.platform.web.TenantResolverFilter;

/** Registers the web Tenant Resolver before Spring Security (AC-002.8). */
@Configuration(proxyBeanMethods = false)
public class TenantFilterConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TenantFilterConfig.class);
    private static final String TEMPLATE = "${TSOLVE_SITE_ORIGIN_TEMPLATE:}";

    @Bean
    FilterRegistrationBean<TenantResolverFilter> tenantResolverRegistration(
            ObjectMapper mapper,
            ThreadLocalTenantContext holder,
            ObjectProvider<WorkspaceCatalog> catalog,
            @Value(TEMPLATE) String siteOriginTemplate) {
        var registration = new FilterRegistrationBean<>(new TenantResolverFilter(
                hostsOrNull(siteOriginTemplate), catalog, holder, new SecurityProblemWriter(mapper)));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        return registration;
    }

    /** The catalog is asked for only after every singleton exists, never while the filter is built. */
    @Bean
    SmartInitializingSingleton tenantResolverStartupCheck(
            ObjectProvider<WorkspaceCatalog> catalog, @Value(TEMPLATE) String siteOriginTemplate) {
        return () -> {
            if (catalog.getIfAvailable() == null) {
                LOG.info("No Workspace catalog: the web Tenant Resolver passes every request through");
            } else {
                HostClassifier.fromTemplate(siteOriginTemplate);
            }
        };
    }

    // Without a catalog a blank template is fine; with one, the start-up check stops before any request.
    private static HostClassifier hostsOrNull(String siteOriginTemplate) {
        try {
            return HostClassifier.fromTemplate(siteOriginTemplate);
        } catch (IllegalStateException noBaseDomain) {
            return null;
        }
    }
}
