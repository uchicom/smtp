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
    protected int pool;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * @param args
     */
    public static void main (String[] args) {
        if (args.length != 5) {
            System.out.println("args.length != 5");
            return;
        }
        //メールフォルダ格納フォルダ
        File dir = new File(args[0]);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("mailbox directory is not found.");
            return;
        }
        //メール
        String hostName = args[1];

        // ポート
        int port = 8025;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back = 10;
        if (args.length == 3) {
            back = Integer.parseInt(args[3]);
        }
        // スレッドプール数
        int pool = 10;
        if (args.length > 4) {
            pool = Integer.parseInt(args[3]);
        }
        execute(hostName, dir, port, back, pool);
    }
    
    public PoolSmtpServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }
    private static void execute(String hostName, File dir, int port, int back, int pool) {
        ExecutorService exec = null;
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), back);
            serverQueue.add(server);

            exec = Executors.newFixedThreadPool(pool);
            while (true) {
                
                Socket socket = server.accept();
                exec.execute(new PoolSmtpServer(hostName, dir, socket));
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
