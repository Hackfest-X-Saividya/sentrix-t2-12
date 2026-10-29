package ai.zerotrace.zeroremediate.service;

public class RemediationException extends RuntimeException {
    private final int status;
    public RemediationException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
