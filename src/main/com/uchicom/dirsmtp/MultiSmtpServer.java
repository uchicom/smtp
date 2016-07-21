/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MultiSmtpServer extends SingleSmtpServer {


	public MultiSmtpServer(SmtpParameter parameter) {
		super(parameter);
	}

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		SmtpParameter parameter = new SmtpParameter(args);
		if (parameter.init(System.err)) {
			MultiSmtpServer server = new MultiSmtpServer(parameter);
			server.execute();
		}
	}

	public void execute() {
		ServerSocket server = null;
		try {
			server = new ServerSocket();
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress(parameter.getPort()),
					parameter.getBacklog());
			this.serverSocket = server;
			Thread thread = new Thread() {
				public void run() {
					for (SmtpProcess process : processList) {
						if (System.currentTimeMillis() - process.getStartTime() > 5 * 1000) {
							process.forceClose();
							processList.remove(process);
						}
					}
				}
			};
			thread.setDaemon(true);
			while (true) {

				final SmtpProcess process = new SmtpProcess(parameter,
						server.accept(), rejectMap);
				processList.add(process);
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
