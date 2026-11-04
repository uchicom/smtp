// (C) 2026 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

public class LineReaderTest {

  private LineReader reader(String text) {
    return new LineReader(new BufferedReader(new StringReader(text)));
  }

  @Test
  public void readLine() throws IOException {
    var reader = reader("abc\r\ndef\nghi\rjkl");

    assertThat(reader.readLine(10, false)).isEqualTo("abc");
    assertThat(reader.readLine(10, false)).isEqualTo("def");
    assertThat(reader.readLine(10, false)).isEqualTo("ghi");
    assertThat(reader.readLine(10, false)).isEqualTo("jkl");
    assertThat(reader.readLine(10, false)).isNull();
  }

  @Test
  public void readLine_emptyLine() throws IOException {
    var reader = reader("\r\nabc\r\n");

    assertThat(reader.readLine(10, false)).isEmpty();
    assertThat(reader.readLine(10, false)).isEqualTo("abc");
  }

  @Test
  public void readLine_tooLong() {
    var reader = reader("abcdef\r\n");

    assertThatThrownBy(() -> reader.readLine(5, false)).isInstanceOf(LineTooLongException.class);
  }

  @Test
  public void readLine_maxLength() throws IOException {
    var reader = reader("abcde\r\nfg\r\n");

    assertThat(reader.readLine(5, false)).isEqualTo("abcde");
    assertThat(reader.isFolded()).isFalse();
    assertThat(reader.readLine(5, true)).isEqualTo("fg");
  }

  @Test
  public void readLine_fold() throws IOException {
    var reader = reader("abcdefghijkl\r\nxyz\r\n");

    assertThat(reader.readLine(5, true)).isEqualTo("abcde");
    assertThat(reader.isFolded()).isTrue();
    assertThat(reader.readLine(5, true)).isEqualTo("fghij");
    assertThat(reader.isFolded()).isTrue();
    assertThat(reader.readLine(5, true)).isEqualTo("kl");
    assertThat(reader.isFolded()).isFalse();
    assertThat(reader.readLine(5, true)).isEqualTo("xyz");
    assertThat(reader.readLine(5, true)).isNull();
  }

  @Test
  public void readLine_foldExactBoundary() throws IOException {
    var reader = reader("abcde\r\nfg");

    assertThat(reader.readLine(5, true)).isEqualTo("abcde");
    assertThat(reader.isFolded()).isFalse();
    assertThat(reader.readLine(5, true)).isEqualTo("fg");
    assertThat(reader.readLine(5, true)).isNull();
  }
}
