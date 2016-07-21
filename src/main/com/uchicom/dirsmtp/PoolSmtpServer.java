/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class PoolSmtpServer extends SingleSmtpServer {

    public PoolSmtpServer(SmtpParameter parameter) {
		super(parameter);
	}

	/**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * @param args
     */
    public static void main (String[] args) {
        SmtpParameter parameter = new SmtpParameter(args);
        if (parameter.init(System.err)) {
        	PoolSmtpServer server = new PoolSmtpServer(parameter);
			server.execute();
        }
    }

    public void execute(SmtpParameter parameter) {
        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			this.serverSocket = server;

            exec = Executors.newFixedThreadPool(parameter.getPool());
            while (true) {
                final SmtpProcess process = new SmtpProcess(parameter, server.accept(), rejectMap);
                exec.execute(new Runnable() {
                	public void run() {
                		process.execute();
                	}
                });
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
            if (exec != null) {
                exec.shutdownNow();
            }
        }
    }


}
