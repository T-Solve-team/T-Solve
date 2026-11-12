package vn.tsolve.identity.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.tsolve.identity.service.CompanyDomainFallback;

@Component
@Profile("identity-mock")
public class MockCompanyDomainFallback implements CompanyDomainFallback {
    private final MockIdentityProperties properties;

    public MockCompanyDomainFallback(MockIdentityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String companyDomain() {
        return properties.companyDomain();
    }
}
