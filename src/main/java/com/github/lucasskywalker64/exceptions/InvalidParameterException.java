package com.github.lucasskywalker64.exceptions;

public class InvalidParameterException extends Exception {

    private final int code;

    /**
     * Creates a new exception with given error code.
     * <p>
     * List of error codes:
     * <li>1001 - Amount of emojis and roles don't match.
     * <li>1002 - Unknown role.
     * <li>1003 - Unknown YouTube channel name.
     *
     * @param code error code
     * @param e the underlying exception
     */
    public InvalidParameterException(int code, Throwable e) {
        super(e);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
