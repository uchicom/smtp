// (C) 2014 uchicom
package com.uchicom.smtp;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.util.Parameter;

/**
 * SMTP実行用のパラメータクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class SmtpParameter extends Parameter {

  /**
   * 引数指定のコンストラクター.
   *
   * @param args 引数
   */
  public SmtpParameter(String[] args) {
    super(args);
  }

  /**
   * 初期化
   *
   * @return 初期化に成功する場合はtrue,それ以外はfalseを返します.
   */
  public boolean init() {
    // メールボックスの基準フォルダ
    if (!is("dir")) {
      put("dir", Constants.DEFAULT_MAILBOX);
    }
    // 実行するサーバのタイプ
    if (!is("type")) {
      put("type", "single");
    }
    // ホスト名
    if (!is("host")) {
      put("host", "localhost");
    }
    // 待ち受けポート
    if (!is("port")) {
      put("port", Constants.DEFAULT_PORT);
    }
    // 受信する接続 (接続要求) のキューの最大長
    if (!is("back")) {
      put("back", Constants.DEFAULT_BACK);
    }
    // プールするスレッド数
    if (!is("pool")) {
      put("pool", Constants.DEFAULT_POOL);
    }
    // メモリ動作
    if (is("memory")) {
      Context context = Context.singleton();
      context.setUsers(get("memory").split(","));
    }
    // 送信可否
    if (is("transfer")) {
      put("transfer", Constants.DEFAULT_TRANSFER);
    }
    return true;
  }

  public Server createServer() {
    Server server = null;
    switch (get("type")) {
      case "multi":
        server =
            new MultiSocketServer(
                this,
                (parameter, socket) -> {
                  return new SmtpProcess(parameter, socket);
                });
        break;
      case "pool":
        server =
            new PoolSocketServer(
                this,
                (parameter, socket) -> {
                  return new SmtpProcess(parameter, socket);
                });
        break;
      case "single":
        server =
            new SingleSocketServer(
                this,
                (parameter, socket) -> {
                  return new SmtpProcess(parameter, socket);
                });
        break;
    }
    return server;
  }
}
