package vn.tsolve.ticket.service.impl;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.ticket.dto.PendingReviewNotification;
import vn.tsolve.ticket.mapper.ReviewMapper;
import vn.tsolve.ticket.repository.ReviewNotificationRepository;
import vn.tsolve.ticket.service.ReviewException;
import vn.tsolve.ticket.service.ReviewNotificationService;

@Service
public class ReviewNotificationServiceImpl implements ReviewNotificationService {
    private final ReviewNotificationRepository repository;
    private final ReviewMapper mapper;

    public ReviewNotificationServiceImpl(ReviewNotificationRepository repository, ReviewMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PendingReviewNotification> pending(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() > 100) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "MSG06");
        }
        return repository
                .findByDeliveredAtIsNull(org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id")))
                .map(mapper::toNotification);
    }

    @Override
    @Transactional
    public void acknowledgeDelivery(long notificationId) {
        repository
                .findById(notificationId)
                .orElseThrow(() -> new ReviewException(HttpStatus.NOT_FOUND, "MSG04"))
                .acknowledgeDelivery(Instant.now());
    }
}
