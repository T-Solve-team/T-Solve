package vn.tsolve.project.dto;

/** Internal result of a Save: the controller answers 201 when {@code created}, otherwise 200, with {@code project}. */
public record ProjectSaveResult(ProjectResponse project, boolean created) {}
