// (c) 2015 uchicom
package com.uchicom.smtp;

import java.io.Writer;
import java.util.List;

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
	 * @throws Exception エラーが発生した場合
	 */
	public Writer getWriter() throws Exception;

	/**
	 * メールを削除します.
	 */
	public void delete();


	/**
	 * メールをコピーします.
	 *
	 * @param boxList メールボックスのリスト
	 * @param senderHostName 送り元ホスト名
	 * @param localHostName ローカルホスト名
	 */
	public void copy(List<MailBox> boxList, String senderHostName, String localHostName);

	/**
	 * データを取得します.
	 *
	 * @return メール本体Data情報
	 */
	public String getData();
}
