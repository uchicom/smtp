/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * @author Uchiyama Shigeki
 * 
 */
public class MultiSmtpServer extends SingleSmtpServer {

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter parameter = new SmtpParameter(args);
		if (parameter.init(System.err)) {
			execute(parameter);
		}
	}

	private static void execute(SmtpParameter parameter) {
		ServerSocket server = null;
		try {
			server = new ServerSocket();
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress(parameter.getPort()),
					parameter.getBacklog());
			serverQueue.add(server);
			while (true) {
				final SmtpProcess process = new SmtpProcess(parameter,
						server.accept());
				new Thread() {
					public void run() {
						process.execute();
					}
				}.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (server != null) {
				try {
					server.close();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					server = null;
				}
			}
		}
	}

}
