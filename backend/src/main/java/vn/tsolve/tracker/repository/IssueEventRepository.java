package vn.tsolve.tracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.tsolve.tracker.entity.IssueEvent;

public interface IssueEventRepository extends JpaRepository<IssueEvent, Long> {}
