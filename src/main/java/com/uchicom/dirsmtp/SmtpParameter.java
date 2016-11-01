/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.PrintStream;

import com.uchicom.server.Parameter;
import com.uchicom.server.Server;

/**
 * SMTP実行用のパラメータクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
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
     * @param ps
     * @return
     */
    public boolean init(PrintStream ps) {
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
		return true;
	}



    public Server createServer() {
    	Server server = null;
		switch (get("type")) {
		case "multi":
			server = new MultiSmtpServer(this);
			break;
		case "pool":
			server = new PoolSmtpServer(this);
			break;
		case "single":
			server = new SingleSmtpServer(this);
			break;
		}
    	return server;
    }
}
