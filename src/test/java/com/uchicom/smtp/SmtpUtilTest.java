// (C) 2026 uchicom
package com.uchicom.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SmtpUtilTest {

  @TempDir Path tempDir;

  @Test
  public void resolveUserDir_valid() throws IOException {
    Path user = Files.createDirectory(tempDir.resolve("user.name-1_a"));

    File result = SmtpUtil.resolveUserDir(tempDir.toFile(), "user.name-1_a");

    assertThat(result).isEqualTo(user.toFile().getCanonicalFile());
  }

  @Test
  public void resolveUserDir_notExists() {
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), "nouser")).isNull();
  }

  @Test
  public void resolveUserDir_file() throws IOException {
    Files.createFile(tempDir.resolve("file"));

    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), "file")).isNull();
  }

  @Test
  public void resolveUserDir_traversal() throws IOException {
    Path base = Files.createDirectory(tempDir.resolve("base"));
    Files.createDirectory(tempDir.resolve("outside"));

    assertThat(SmtpUtil.resolveUserDir(base.toFile(), "../outside")).isNull();
    assertThat(SmtpUtil.resolveUserDir(base.toFile(), "..")).isNull();
    assertThat(SmtpUtil.resolveUserDir(base.toFile(), ".")).isNull();
    assertThat(SmtpUtil.resolveUserDir(base.toFile(), "..\\outside")).isNull();
  }

  @Test
  public void resolveUserDir_absolutePath() {
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), tempDir.toString())).isNull();
  }

  @Test
  public void resolveUserDir_invalid() {
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), null)).isNull();
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), "")).isNull();
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), "a/b")).isNull();
    assertThat(SmtpUtil.resolveUserDir(tempDir.toFile(), "a b")).isNull();
  }
}
