package com.github.lucasskywalker64.exceptions;

public class InvalidParameters extends Exception {

  private final int code;

  /**
   * Creates a new exception with given error code.
   * <p>
   * List of error codes:
   * <li>1001 - Amount of emojis and roles don't match.
   * <li>1002 - Unknown role
   * @param code error code
   */
  public InvalidParameters(int code) {
    super();
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
