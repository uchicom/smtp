// (c) 2015 uchicom
package com.uchicom.smtp;

import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * メールのインターフェース
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public interface Mail {

	/**
	 * 受信メール書き込み用のライターを取得します.
	 *
	 * @return ライター
	 * @throws Exception
	 */
	public Writer getWriter() throws Exception;

	/**
	 * メールを削除します.
	 */
	public void delete();

	/**
	 *
	 * @return
	 */
	public Map<String, String> getMap();

	/**
	 * メールをコピーします.
	 *
	 * @param boxList
	 */
	public void copy(List<MailBox> boxList, String senderHostName, String localHostName);

	/**
	 * タイトルを取得します.
	 *
	 * @return
	 */
	public String getTitle();
}
