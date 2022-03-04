// (C) 2015 uchicom
package com.uchicom.smtp;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Main {

  /**
   * アドレスとメールユーザーフォルダの格納フォルダを指定する.
   *
   * @param args パラメータ引数
   */
  public static void main(String[] args) {
    SmtpParameter parameter = new SmtpParameter(args);
    if (parameter.init()) {
      parameter.createServer().execute();
    }
  }
}
