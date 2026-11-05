// (C) 2026 uchicom
package com.uchicom.smtp;

import java.util.HashMap;
import java.util.Map;

/**
 * 同時接続数の制限を管理するクラス. 全体とIPごとの上限を扱う.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class ConnectionLimiter {

  private static final ConnectionLimiter INSTANCE = new ConnectionLimiter();

  private final Map<String, Integer> perIp = new HashMap<>();
  private int total;

  public static ConnectionLimiter singleton() {
    return INSTANCE;
  }

  /**
   * 接続を登録する.
   *
   * @param ip 接続元IP
   * @param maxConn 全体の上限
   * @param maxConnPerIp IPごとの上限
   * @return 上限内で登録できた場合はtrue。上限超過の場合は登録せずfalse
   */
  public synchronized boolean acquire(String ip, int maxConn, int maxConnPerIp) {
    int count = perIp.getOrDefault(ip, 0);
    if (total >= maxConn || count >= maxConnPerIp) {
      return false;
    }
    total++;
    perIp.put(ip, count + 1);
    return true;
  }

  /**
   * 接続を解放する.
   *
   * @param ip 接続元IP
   */
  public synchronized void release(String ip) {
    Integer count = perIp.get(ip);
    if (count == null) {
      return;
    }
    total--;
    if (count <= 1) {
      perIp.remove(ip);
    } else {
      perIp.put(ip, count - 1);
    }
  }

  synchronized int getTotal() {
    return total;
  }
}
