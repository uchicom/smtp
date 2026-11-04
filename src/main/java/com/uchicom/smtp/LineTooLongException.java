// (C) 2026 uchicom
package com.uchicom.smtp;

import java.io.IOException;

/**
 * 受信行長が上限を超えた場合の例外.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class LineTooLongException extends IOException {

  public LineTooLongException() {
    super("Line too long");
  }
}
