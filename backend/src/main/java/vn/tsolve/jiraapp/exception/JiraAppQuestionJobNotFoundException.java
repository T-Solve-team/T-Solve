package vn.tsolve.jiraapp.exception;

import java.util.Map;
import vn.tsolve.common.exception.NotFoundException;

public class JiraAppQuestionJobNotFoundException extends NotFoundException {
    public JiraAppQuestionJobNotFoundException() {
        super("MSG06", Map.of());
    }
}
