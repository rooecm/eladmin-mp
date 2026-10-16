package fun.rooe.admin.error;

/**
 * Stable error identity and the default message safe to display to a client.
 */
public interface ErrorCode {
    String getCode();

    String getMessage();
}
