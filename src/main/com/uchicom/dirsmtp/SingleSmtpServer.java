/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Uchiyama Shigeki
 * 
 */
public class SingleSmtpServer {

	/**
	 * 複数のサーバーを実行する場合に格納されるキュー
	 */
	protected static Queue<ServerSocket> serverQueue = new ConcurrentLinkedQueue<ServerSocket>();

	protected static Map<String, Integer> rejectMap = new HashMap<String, Integer>();
	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter param = new SmtpParameter(args);
		if (param.init(System.err)) {
			execute(param);
		}
	}

	/**
	 * 処理実行.
	 * 
	 * @param hostName
	 * @param file
	 * @param port
	 * @param back
	 */
	private static void execute(SmtpParameter parameter) {
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			serverQueue.add(serverSocket);
			while (true) {
				SmtpProcess process = new SmtpProcess(parameter, serverSocket.accept());
				process.execute(rejectMap);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					serverSocket = null;
				}
			}
		}
	}

	/**
	 * シャットダウン処理.
	 * @param args
	 */
	public static void shutdown(String... args) {
		if (!serverQueue.isEmpty()) {
			try {
				serverQueue.poll().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
