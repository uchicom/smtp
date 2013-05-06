/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Uchiyama Shigeki
 *
 */
public class MultiSmtpServer extends SingleSmtpServer implements Runnable {

    protected Socket socket;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * @param args
     */
    public static void main (String[] args) {
        if (args.length != 2) {
            System.out.println("args.length != 2");
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
        execute(hostName, dir, port, back);
    }
    
    public MultiSmtpServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }
    

    private static void execute(String hostName, File dir, int port, int back) {
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port), back);
            serverQueue.add(server);
            while (true) {
                Socket socket = server.accept();
                new Thread(new MultiSmtpServer(hostName, dir, socket)).start();
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
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        smtp(socket);
    }
    

}
