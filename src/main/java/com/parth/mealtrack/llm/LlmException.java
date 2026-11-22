package com.parth.mealtrack.llm;

public class LlmException extends RuntimeException {
    private final boolean retryable;

    public LlmException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** True for transient failures (timeout, 5xx, 429). Bad output or a bad key will not improve on retry. */
    public boolean isRetryable() { return retryable; }
}
