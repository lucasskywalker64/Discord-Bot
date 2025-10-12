package com.github.lucasskywalker64.exceptions;

public class InvalidParameterException extends Exception {

    private final int code;

    /**
     * Creates a new exception with given error code and exception.
     * <p>
     * List of error codes:
     * <li>1001 - Unknown YouTube channel name.
     * <li>1002 - Unknown ticket channel.
     *
     * @param code error code
     * @param e the underlying exception
     */
    public InvalidParameterException(int code, Throwable e) {
        super(e);
        this.code = code;
    }

    /**
     * Creates a new exception with given error code.
     * <p>
     * List of error codes:
     * <li>1001 - Unknown YouTube channel name.
     * <li>1002 - Unknown ticket channel.
     *
     * @param code error code
     */
    public InvalidParameterException(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
