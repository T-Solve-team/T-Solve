package vn.tsolve.knowledge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tsolve.common.exception.NotFoundException;

@Service
public class SolutionService {

    private final SolutionRepository repository;

    public SolutionService(SolutionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Solution createDraft(SolutionRequest r) {
        Solution s = new Solution(r.workspaceId(), r.title());
        s.edit(r);
        return repository.save(s);
    }

    @Transactional
    public Solution update(Long id, SolutionRequest r) {
        Solution s = get(id);
        s.edit(r);
        return s;
    }

    @Transactional
    public Solution transition(Long id, SolutionStatus next) {
        Solution s = get(id);
        s.transitionTo(next);
        return s;
    }

    /** Only PUBLISHED solutions are returned unless a status is requested explicitly. */
    @Transactional(readOnly = true)
    public List<Solution> list(SolutionStatus status) {
        return repository.findByStatus(status == null ? SolutionStatus.PUBLISHED : status);
    }

    @Transactional(readOnly = true)
    public Solution get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Solution " + id + " not found"));
    }
}
