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
  public void verifyPassword_ok() throws IOException {
    Path user = Files.createDirectory(tempDir.resolve("user"));
    Files.writeString(user.resolve(Constants.PASSWORD_FILE_NAME), "\nsecret\n");

    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "user", "secret")).isTrue();
  }

  @Test
  public void verifyPassword_mismatch() throws IOException {
    Path user = Files.createDirectory(tempDir.resolve("user"));
    Files.writeString(user.resolve(Constants.PASSWORD_FILE_NAME), "secret\n");

    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "user", "wrong")).isFalse();
    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "user", "")).isFalse();
  }

  @Test
  public void verifyPassword_noPasswordFile() throws IOException {
    Files.createDirectory(tempDir.resolve("user"));

    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "user", "secret")).isFalse();
  }

  @Test
  public void verifyPassword_emptyPasswordFile() throws IOException {
    Path user = Files.createDirectory(tempDir.resolve("user"));
    Files.writeString(user.resolve(Constants.PASSWORD_FILE_NAME), "\n\n");

    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "user", "")).isFalse();
  }

  @Test
  public void verifyPassword_noUser() {
    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "nouser", "secret")).isFalse();
    assertThat(SmtpUtil.verifyPassword(tempDir.toFile(), "../x", "secret")).isFalse();
  }

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

  @Test
  public void parseAddress_ok() {
    assertThat(SmtpUtil.parseAddress("MAIL FROM:<a@example.com>", false))
        .containsExactly("a", "example.com");
    assertThat(SmtpUtil.parseAddress("RCPT TO: a@example.com", false))
        .containsExactly("a", "example.com");
    assertThat(SmtpUtil.parseAddress("MAIL FROM:<a@example.com> SIZE=100", false))
        .containsExactly("a", "example.com");
  }

  @Test
  public void parseAddress_ng() {
    assertThat(SmtpUtil.parseAddress("MAIL FROM", false)).isNull();
    assertThat(SmtpUtil.parseAddress("MAIL FROM:", false)).isNull();
    assertThat(SmtpUtil.parseAddress("MAIL FROM:<>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("MAIL FROM:abc", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<@example.com>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<a@>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<a@@example.com>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<../a@example.com>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<a@example..com>", false)).isNull();
    assertThat(SmtpUtil.parseAddress("RCPT TO:<" + "a".repeat(65) + "@example.com>", false))
        .isNull();
  }

  @Test
  public void parseAddress_nullPath() {
    assertThat(SmtpUtil.parseAddress("MAIL FROM:<>", true)).containsExactly("", "");
    assertThat(SmtpUtil.parseAddress("MAIL FROM:<> SIZE=100", true)).containsExactly("", "");
    assertThat(SmtpUtil.parseAddress("RCPT TO:<>", false)).isNull();
  }
}
