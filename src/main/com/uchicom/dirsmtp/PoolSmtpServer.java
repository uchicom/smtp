/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Uchiyama Shigeki
 *
 */
public class PoolSmtpServer extends SingleSmtpServer implements Runnable {

    protected Socket socket;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * @param args
     */
    public static void main (String[] args) {
        Parameter param = new Parameter(args);
        if (param.init(System.err)) {
            execute(param);
        }
    }
    
    public PoolSmtpServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }
    private static void execute(Parameter param) {
        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(param.getPort()), param.getBack());
            serverQueue.add(server);

            exec = Executors.newFixedThreadPool(param.getPool());
            while (true) {
                
                Socket socket = server.accept();
                exec.execute(new PoolSmtpServer(param.getHostName(), param.getBase(), socket));
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
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        smtp(socket);
    }
    

}
