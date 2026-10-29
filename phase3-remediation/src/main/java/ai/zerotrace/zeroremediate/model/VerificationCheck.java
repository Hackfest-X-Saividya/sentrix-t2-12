package ai.zerotrace.zeroremediate.model;

public record VerificationCheck(String checkId, Status status, String summary, String evidence) {
    public enum Status { PASSED, FAILED, NOT_RUN }
}
