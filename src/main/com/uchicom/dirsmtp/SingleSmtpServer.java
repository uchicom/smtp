/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SingleSmtpServer {

	/**
	 * 複数のサーバーを実行する場合に格納されるキュー
	 */
	protected ServerSocket serverSocket;

	protected static Map<String, Integer> rejectMap = new HashMap<String, Integer>();

	protected SmtpParameter parameter;

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter param = new SmtpParameter(args);
		if (param.init(System.err)) {
			SingleSmtpServer server = new SingleSmtpServer(param);
			server.execute();
		}
	}

	public SingleSmtpServer(SmtpParameter parameter) {
		this.parameter = parameter;
	}
	/**
	 * 処理実行.
	 *
	 * @param hostName
	 * @param file
	 * @param port
	 * @param back
	 */
	public void execute() {
		try (ServerSocket serverSocket = new ServerSocket()){
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			this.serverSocket = serverSocket;
			while (true) {
				SmtpProcess process = new SmtpProcess(parameter, serverSocket.accept());
				process.execute(rejectMap, System.out);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public List<Mail> getMailList(String user) {
		if (parameter.isMemory()) {
			return parameter.getMailList(user);
		} else {
			List<Mail> mailList = new ArrayList<>();
			File box = new File(parameter.getBase(), user);
			if (box.exists()) {
				for (File file : box.listFiles()) {
					try {
						mailList.add(new FileMail(file));
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
				}
			}
			return mailList;
		}
	}

	/**
	 * シャットダウン処理.
	 * @param args
	 */
	public void shutdown(String... args) {
		if (serverSocket != null && !serverSocket.isClosed()) {
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
