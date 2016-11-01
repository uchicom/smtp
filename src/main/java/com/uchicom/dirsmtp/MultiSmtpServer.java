/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.ServerSocket;

import com.uchicom.server.AbstractSocketServer;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class MultiSmtpServer extends AbstractSocketServer {


	public MultiSmtpServer(SmtpParameter parameter) {
		super(parameter);
	}


	/* (非 Javadoc)
	 * @see com.uchicom.dirsmtp.AbstractSocketServer#execute(java.net.ServerSocket)
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
			final SmtpProcess process = new SmtpProcess(parameter,
					serverSocket.accept());
			processList.add(process);
			new Thread() {
				public void run() {
					process.execute();
				}
			}.start();
		}
	}

}
