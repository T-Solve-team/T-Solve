package vn.tsolve.worker.dto;

public record OutboundEmail(String recipient, String subject, String bodyText, String bodyHtml) {}
