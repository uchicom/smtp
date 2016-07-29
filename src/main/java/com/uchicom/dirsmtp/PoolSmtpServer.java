/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class PoolSmtpServer extends AbstractSocketServer {
	ExecutorService exec;
    public PoolSmtpServer(SmtpParameter parameter) {
		super(parameter);
		exec = Executors.newFixedThreadPool(parameter.getPool());
	}


	/* (非 Javadoc)
	 * @see com.uchicom.dirsmtp.AbstractSocketServer#execute(java.net.ServerSocket)
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
            final SmtpProcess process = new SmtpProcess(parameter, serverSocket.accept(), rejectMap);
            exec.execute(new Runnable() {
            	public void run() {
            		process.execute();
            	}
            });
        }
	}


}
