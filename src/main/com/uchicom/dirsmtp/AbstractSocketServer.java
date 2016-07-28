/**
 * (c) 2016 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 抽象ソケットサーバ.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public abstract class AbstractSocketServer implements Server {
	/**
	 * 複数のサーバーを実行する場合に格納されるキュー
	 */
	protected ServerSocket serverSocket;

	protected static Map<String, Integer> rejectMap = new ConcurrentHashMap<String, Integer>();

	protected List<SmtpProcess> processList = new CopyOnWriteArrayList<SmtpProcess>();
	protected SmtpParameter parameter;

	public AbstractSocketServer(SmtpParameter parameter) {
		this.parameter = parameter;
	}

	/* (非 Javadoc)
	 * @see com.uchicom.dirsmtp.Server#execute()
	 */
	@Override
	public void execute() {
		try (ServerSocket serverSocket = new ServerSocket()){
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			this.serverSocket = serverSocket;
			Thread thread = new Thread() {
				public void run() {
					while(true) {
						for (SmtpProcess process : processList) {
							if (System.currentTimeMillis() - process.getStartTime() > 10 * 1000) {
								process.forceClose();
								processList.remove(process);
							}
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO 自動生成された catch ブロック
							e.printStackTrace();
						}
					}
				}
			};
			thread.setDaemon(true);
			thread.start();
			execute(serverSocket);
			while (true) {
				SmtpProcess process = new SmtpProcess(parameter, serverSocket.accept(), rejectMap);
				process.execute();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected abstract void execute(ServerSocket serverSocket) throws IOException;



	/**
	 * シャットダウン処理.
	 * @param args
	 */
	public void shutdown(String... args) {
		if (serverSocket != null && !serverSocket.isClosed()) {
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	public List<Mail> getMailList(String user) {
		if (parameter.isMemory()) {
			return parameter.getMailList(user);
		} else {
			List<Mail> mailList = new ArrayList<>();
			File box = new File(parameter.getBase(), user);
			if (box.exists()) {
				for (File file : box.listFiles()) {
					try {
						mailList.add(new FileMail(file));
					} catch (IOException e) {
						// TODO 自動生成された catch ブロック
						e.printStackTrace();
					}
				}
			}
			return mailList;
		}
	}

}
