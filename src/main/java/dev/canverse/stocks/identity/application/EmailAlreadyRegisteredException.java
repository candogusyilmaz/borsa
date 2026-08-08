package dev.canverse.stocks.identity.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public static final String CODE = "identity.email_already_registered";

    private static final String MESSAGE = "The email address is already registered.";

    public EmailAlreadyRegisteredException() {
        super(MESSAGE);
    }

    public EmailAlreadyRegisteredException(Throwable cause) {
        super(MESSAGE, cause);
    }

    public String getCode() {
        return CODE;
    }
}
