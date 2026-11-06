// (C) 2014 uchicom
package com.uchicom.smtp;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.server.VirtualSocketServer;
import com.uchicom.smtp.factory.di.DIFactory;
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
    // DKIMセレクター
    if (!is("dkimSelector")) {
      put("dkimSelector", Constants.DEFAULT_DKIM_SELECTOR);
    }
    // 本文行長上限(CRLFを除く文字数)
    if (!is("maxLine")) {
      put("maxLine", Constants.DEFAULT_MAX_LINE);
    }
    // コマンド行長上限(CRLFを除く文字数)
    if (!is("maxCommandLine")) {
      put("maxCommandLine", Constants.DEFAULT_MAX_COMMAND_LINE);
    }
    // 1通あたりの宛先数上限
    if (!is("maxRcpt")) {
      put("maxRcpt", Constants.DEFAULT_MAX_RCPT);
    }
    // メッセージサイズ上限(バイト)
    if (!is("maxSize")) {
      put("maxSize", Constants.DEFAULT_MAX_SIZE);
    }
    // ソケットタイムアウト(ミリ秒)
    if (!is("soTimeout")) {
      put("soTimeout", Constants.DEFAULT_SO_TIMEOUT);
    }
    // 同時接続数上限
    if (!is("maxConn")) {
      put("maxConn", Constants.DEFAULT_MAX_CONN);
    }
    // IPごとの同時接続数上限
    if (!is("maxConnPerIp")) {
      put("maxConnPerIp", Constants.DEFAULT_MAX_CONN_PER_IP);
    }
    return true;
  }

  public Server createServer() {
    var logger = DIFactory.logger();
    return switch (get("type")) {
      case "multi" ->
          new MultiSocketServer(
              this,
              (parameter, socket) -> {
                return new SmtpProcess(parameter, logger, socket);
              });
      case "pool" ->
          new PoolSocketServer(
              this,
              (parameter, socket) -> {
                return new SmtpProcess(parameter, logger, socket);
              });
      case "virtual" ->
          new VirtualSocketServer(
              this,
              (parameter, socket) -> {
                return new SmtpProcess(parameter, logger, socket);
              });
      case "single" ->
          new SingleSocketServer(
              this,
              (parameter, socket) -> {
                return new SmtpProcess(parameter, logger, socket);
              });
      default -> throw new IllegalArgumentException("Unknown server type: " + get("type"));
    };
  }
}
