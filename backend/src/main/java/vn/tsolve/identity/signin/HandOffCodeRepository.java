package vn.tsolve.identity.signin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandOffCodeRepository extends JpaRepository<HandOffCode, UUID> {}
