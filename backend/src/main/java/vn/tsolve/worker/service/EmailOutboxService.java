package vn.tsolve.worker.service;

import java.util.UUID;
import vn.tsolve.worker.dto.OutboundEmail;

public interface EmailOutboxService {
    UUID queue(OutboundEmail email);
}
