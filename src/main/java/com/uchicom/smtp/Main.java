// (C) 2015 uchicom
package com.uchicom.smtp;

import com.uchicom.server.Server;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Main {

  static Server server;

  /**
   * アドレスとメールユーザーフォルダの格納フォルダを指定する.
   *
   * @param args パラメータ引数
   */
  public static void main(String[] args) {
    SmtpParameter parameter = new SmtpParameter(args);
    if (parameter.init()) {
      server = parameter.createServer();
      server.execute();
    }
  }

  public static void shutdown() {
    if (server != null) {
      server.stop();
    }
  }
}
