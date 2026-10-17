package edu.psu.hcdd412.gameflix.auth.auth;

/** One exception, one message, for every way a login can fail. */
public class InvalidCredentialsException extends RuntimeException {
    public static final String MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
