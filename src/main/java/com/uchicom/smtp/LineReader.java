// (C) 2026 uchicom
package com.uchicom.smtp;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * 行長を制限しながら1行ずつ読み込むクラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class LineReader {

  private final BufferedReader reader;
  private boolean folded;

  public LineReader(BufferedReader reader) {
    this.reader = reader;
  }

  /**
   * 1行読み込む.
   *
   * @param maxLine 行長上限(改行を除く文字数)
   * @param fold 上限を超えた場合に、その位置で折り返して続きを次の行として返すならtrue。falseの場合は例外とする
   * @return 行(改行を除く)。終端の場合はnull
   * @throws LineTooLongException 行長が上限を超えて、foldがfalseの場合
   * @throws IOException 通信エラーの場合
   */
  public String readLine(int maxLine, boolean fold) throws IOException {
    folded = false;
    StringBuilder sb = new StringBuilder();
    boolean read = false;
    while (true) {
      reader.mark(1);
      int c = reader.read();
      if (c < 0) {
        return read ? sb.toString() : null;
      }
      read = true;
      if (c == '\n') {
        return sb.toString();
      }
      if (c == '\r') {
        reader.mark(1);
        if (reader.read() != '\n') {
          reader.reset();
        }
        return sb.toString();
      }
      if (sb.length() >= maxLine) {
        if (!fold) {
          throw new LineTooLongException();
        }
        // 折り返し: この文字は次の行の先頭として読み直す
        reader.reset();
        folded = true;
        return sb.toString();
      }
      sb.append((char) c);
    }
  }

  /**
   * 直前に読み込んだ行が折り返しにより途中で切られたかを返す.
   *
   * @return 続きが次の行として返される場合はtrue
   */
  public boolean isFolded() {
    return folded;
  }
}
