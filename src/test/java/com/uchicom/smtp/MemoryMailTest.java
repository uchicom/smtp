// (C) 2025 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class MemoryMailTest {

  /** 不正なUTF-8を含む8bitデータがバイト単位で保持されること. */
  @Test
  public void getDataKeepsRawBytes() throws Exception {
    MemoryMail mail = new MemoryMail();
    String line =
        new String(
            new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0x80, 'a'}, StandardCharsets.ISO_8859_1);
    try (Writer writer = mail.getWriter()) {
      writer.write(line);
    }

    assertThat(mail.getData().getBytes(StandardCharsets.ISO_8859_1))
        .containsExactly((byte) 0xff, (byte) 0xfe, (byte) 0x80, 'a');
  }
}
