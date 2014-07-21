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
 * @author Uchiyama Shigeki
 *
 */
public class PoolSmtpServer extends SingleSmtpServer {

    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * @param args
     */
    public static void main (String[] args) {
        SmtpParameter parameter = new SmtpParameter(args);
        if (parameter.init(System.err)) {
            execute(parameter);
        }
    }

    private static void execute(SmtpParameter parameter) {
        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
            serverQueue.add(server);

            exec = Executors.newFixedThreadPool(parameter.getPool());
            while (true) {
                final SmtpProcess process = new SmtpProcess(parameter, server.accept());
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
