// (C) 2015 uchicom
package com.uchicom.smtp;

import java.io.Writer;
import java.util.Set;

/**
 * メールのインターフェース
 *
 * @author uchicom: Shigeki Uchiyama
 */
public interface Mail {

  /**
   * 受信メール書き込み用のライターを取得します.
   *
   * @return ライター
   * @throws Exception エラーが発生した場合
   */
  public Writer getWriter() throws Exception;

  /** メールを削除します. */
  public void delete();

  /**
   * メールをコピーします.
   *
   * @param boxSet メールボックスのセット
   * @param localHostName ローカルホスト名
   * @param senderHostName 送り元ホスト名
   */
  public void copy(Set<MailBox> boxSet, String localHostName, String senderHostName);

  /**
   * データを取得します.
   *
   * @return メール本体Data情報
   */
  public String getData();
}
