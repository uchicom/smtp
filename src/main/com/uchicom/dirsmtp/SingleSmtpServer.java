/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirsmtp;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SingleSmtpServer extends AbstractSocketServer {


	/**
	 * 複数のサーバーを実行する場合に格納されるキュー
	 */
	protected ServerSocket serverSocket;

	protected static Map<String, Integer> rejectMap = new ConcurrentHashMap<String, Integer>();

	protected List<SmtpProcess> processList = new CopyOnWriteArrayList<SmtpProcess>();
	protected SmtpParameter parameter;
	/**
	 * @param parameter
	 */
	public SingleSmtpServer(SmtpParameter parameter) {
		super(parameter);
	}

	/* (非 Javadoc)
	 * @see com.uchicom.dirsmtp.AbstractSocketServer#execute(java.net.ServerSocket)
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
			SmtpProcess process = new SmtpProcess(parameter, serverSocket.accept(), rejectMap);
			process.execute();
		}
	}

}
