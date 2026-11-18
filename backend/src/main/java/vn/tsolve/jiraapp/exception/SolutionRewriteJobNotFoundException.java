package vn.tsolve.jiraapp.exception;

import java.util.Map;
import vn.tsolve.common.exception.NotFoundException;

public class SolutionRewriteJobNotFoundException extends NotFoundException {
    public SolutionRewriteJobNotFoundException() {
        super("MSG68", Map.of());
    }
}
