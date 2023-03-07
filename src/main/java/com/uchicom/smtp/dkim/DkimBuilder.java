// (C) 2022 uchicom
package com.uchicom.smtp.dkim;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class DkimBuilder {
  MimeMessage message;
  String fromHost;
  PrivateKey privateKey;
  String selector;

  public DkimBuilder() {}

  public DkimBuilder message(MimeMessage message) {
    this.message = message;
    return this;
  }

  public DkimBuilder fromHost(String fromHost) {
    this.fromHost = fromHost;
    return this;
  }

  public DkimBuilder selector(String selector) {
    this.selector = selector;
    return this;
  }

  public DkimBuilder privateKeyFile(File privateKeyFile)
      throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
    privateKey(createPrivateKey(privateKeyFile));
    return this;
  }

  public DkimBuilder privateKey(PrivateKey privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public String build() throws Exception {
    StringBuilder builder = new StringBuilder();

    builder
        .append("v=1; ") // バージョン
        .append("a=rsa-sha256; ") // 署名の作成に利用したアルゴリズム
        .append("b=; ") // 電子署名データ
        .append("bh=") // 本文のハッシュ値
        .append(createBh(normalizeBody()))
        .append("; ")
        .append("c=relaxed/relaxed; ") // メール本文の正規化方式
        .append("d=") // ドメイン名
        .append(fromHost)
        .append("; ")
        .append("h=subject:from; ") // 署名したヘッダ
        .append("s=") // セレクタ
        .append(selector)
        .append("; ")
        .append("t=") // 秒数
        .append(getEpocSecond(LocalDateTime.now()));
    String dkim = builder.toString();
    String b = createB(normalizeHeader(), "dkim-signature:" + dkim);
    return dkim.replace("b=;", "b=" + b + ";");
  }

  String createBh(String text) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(text.getBytes());
    return Base64.getEncoder().encodeToString(md.digest());
  }

  String createB(String normalizeHeader, String dkim) throws Exception {
    StringBuilder builder = new StringBuilder(1024);
    builder.append(normalizeHeader);
    builder.append(dkim);
    return Base64.getEncoder().encodeToString(getSign(builder.toString()));
  }

  String normalizeHeader() throws MessagingException {
    StringBuilder builder = new StringBuilder(1024);
    builder
        .append("subject:")
        .append(
            Optional.ofNullable(message.getHeader("Subject", null))
                .orElse("")
                .replaceAll("[ \r\n\t]+", " ")
                .trim())
        .append("\r\n")
        .append("from:")
        .append(
            Optional.ofNullable(message.getHeader("From", null))
                .orElse("")
                .replaceAll("[ \r\n\t]+", " ")
                .trim())
        .append("\r\n");
    return builder.toString().replaceAll(" +", " ");
  }

  String normalizeBody() throws IOException, MessagingException {
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(message.getRawInputStream()))) {
      StringBuilder builder = new StringBuilder(1024);
      List<String> lineList =
          br.lines()
              .map(line -> line.replaceAll(" +", " "))
              .map(line -> line.replaceAll(" $", ""))
              .collect(Collectors.toList());
      int maxIndex = 0;
      for (int i = lineList.size() - 1; i >= 0; i--) {
        if (lineList.get(i).length() > 0) {
          maxIndex = i;
          break;
        }
      }
      if (maxIndex == 0 && lineList.get(0).isEmpty()) {
        return "";
      }
      for (int i = 0; i <= maxIndex; i++) {
        builder.append(lineList.get(i));
        builder.append("\r\n");
      }
      System.out.println(builder.toString());
      return builder.toString();
    }
  }

  PrivateKey createPrivateKey(File file)
      throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(Files.readAllBytes(file.toPath()));
    return keyFactory.generatePrivate(privSpec);
  }

  byte[] getSign(String message) throws Exception {
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update(message.getBytes("UTF-8"));
    return signer.sign();
  }

  long getEpocSecond(LocalDateTime localDateTime) {
    return localDateTime.toEpochSecond(ZoneOffset.UTC);
  }
}
