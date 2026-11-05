// (C) 2026 uchicom
package com.uchicom.smtp.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * SCRAM認証機構(RFC 5802)のサーバー側. チャネルバインディングはtls-server-end-point(-PLUS)のみ対応.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class ScramMechanism implements SaslMechanism {

  private static final SecureRandom RANDOM = new SecureRandom();

  private enum State {
    CLIENT_FIRST,
    CLIENT_FINAL,
    CONFIRM,
    DONE
  }

  private final ScramAlgorithm algorithm;
  private final ScramCredentialSource source;
  private final boolean plus;
  private final byte[] bindingData;
  private State state = State.CLIENT_FIRST;
  private String user;
  private String gs2Header;
  private String clientFirstBare;
  private String serverFirst;
  private String nonce;
  private ScramCredential credential;

  /**
   * チャネルバインディングを提供しない機構を作る.
   *
   * @param algorithm アルゴリズム
   * @param source 資格情報の取得元
   */
  public ScramMechanism(ScramAlgorithm algorithm, ScramCredentialSource source) {
    this(algorithm, source, false, null);
  }

  /**
   * 機構を作る.
   *
   * @param algorithm アルゴリズム
   * @param source 資格情報の取得元
   * @param plus -PLUS機構(チャネルバインディング必須)の場合はtrue
   * @param bindingData サーバーが提供できるtls-server-end-pointのデータ。-PLUSを広告していない場合はnull。
   *     非PLUS機構でもnullでなければ-PLUS広告中として、クライアントのダウングレード(y)を拒否する
   */
  public ScramMechanism(
      ScramAlgorithm algorithm, ScramCredentialSource source, boolean plus, byte[] bindingData) {
    if (plus && bindingData == null) {
      throw new IllegalArgumentException("bindingData is required for -PLUS");
    }
    this.algorithm = algorithm;
    this.source = source;
    this.plus = plus;
    this.bindingData = bindingData;
  }

  @Override
  public byte[] step(byte[] response) throws SaslAuthException {
    String message = new String(response, StandardCharsets.UTF_8);
    switch (state) {
      case CLIENT_FIRST:
        return clientFirst(message);
      case CLIENT_FINAL:
        return clientFinal(message);
      case CONFIRM:
        // server-finalに対するクライアントの空応答
        if (response.length != 0) {
          throw new SaslAuthException("unexpected response");
        }
        state = State.DONE;
        return new byte[0];
      default:
        throw new SaslAuthException("already completed");
    }
  }

  @Override
  public boolean isComplete() {
    return state == State.DONE;
  }

  @Override
  public String getUser() {
    return isComplete() ? user : null;
  }

  private byte[] clientFirst(String message) throws SaslAuthException {
    // gs2-header = gs2-cbind-flag "," [authzid] ","
    String[] parts = message.split(",", 3);
    if (parts.length < 3) {
      throw new SaslAuthException("invalid client-first");
    }
    String flag = parts[0];
    if (plus) {
      // -PLUS機構は、サーバーが対応する種別のチャネルバインディングを必須とする
      if (!flag.equals("p=" + ChannelBinding.TYPE_SERVER_END_POINT)) {
        throw new SaslAuthException("channel binding required");
      }
    } else if ("y".equals(flag)) {
      // サーバーが-PLUSを広告しているのに、クライアントが対応を知らないと主張した場合はダウングレード
      if (bindingData != null) {
        throw new SaslAuthException("channel binding downgrade detected");
      }
    } else if (!"n".equals(flag)) {
      // 非PLUS機構でのp=は不正
      throw new SaslAuthException("channel binding not supported");
    }
    if (!parts[1].isEmpty()) {
      throw new SaslAuthException("authzid not supported");
    }
    gs2Header = flag + ",,";
    clientFirstBare = parts[2];
    String[] attrs = clientFirstBare.split(",");
    if (attrs.length < 2 || !attrs[0].startsWith("n=") || !attrs[1].startsWith("r=")) {
      throw new SaslAuthException("invalid client-first-bare");
    }
    user = attrs[0].substring(2).replace("=2C", ",").replace("=3D", "=");
    String clientNonce = attrs[1].substring(2);
    if (user.isEmpty() || clientNonce.isEmpty()) {
      throw new SaslAuthException("invalid client-first-bare");
    }
    credential = source.find(user, algorithm);
    byte[] serverNonce = new byte[18];
    RANDOM.nextBytes(serverNonce);
    nonce = clientNonce + Base64.getEncoder().encodeToString(serverNonce);
    serverFirst =
        "r="
            + nonce
            + ",s="
            + Base64.getEncoder().encodeToString(credential.salt())
            + ",i="
            + credential.iterations();
    state = State.CLIENT_FINAL;
    return serverFirst.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] clientFinal(String message) throws SaslAuthException {
    int proofIndex = message.lastIndexOf(",p=");
    if (proofIndex < 0) {
      throw new SaslAuthException("invalid client-final");
    }
    String withoutProof = message.substring(0, proofIndex);
    String[] attrs = withoutProof.split(",");
    if (attrs.length != 2 || !attrs[0].startsWith("c=") || !attrs[1].startsWith("r=")) {
      throw new SaslAuthException("invalid client-final");
    }
    byte[] proof;
    try {
      byte[] channelBinding = Base64.getDecoder().decode(attrs[0].substring(2));
      byte[] gs2 = gs2Header.getBytes(StandardCharsets.US_ASCII);
      byte[] expected = plus ? concat(gs2, bindingData) : gs2;
      if (!MessageDigest.isEqual(expected, channelBinding)) {
        throw new SaslAuthException("channel binding mismatch");
      }
      proof = Base64.getDecoder().decode(message.substring(proofIndex + 3));
    } catch (IllegalArgumentException e) {
      throw new SaslAuthException("invalid base64");
    }
    if (!nonce.equals(attrs[1].substring(2))) {
      throw new SaslAuthException("nonce mismatch");
    }
    byte[] authMessage =
        (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes(StandardCharsets.UTF_8);
    byte[] clientSignature = algorithm.hmac(credential.storedKey(), authMessage);
    if (proof.length != clientSignature.length) {
      throw new SaslAuthException("authentication failed");
    }
    byte[] clientKey = new byte[proof.length];
    for (int i = 0; i < proof.length; i++) {
      clientKey[i] = (byte) (proof[i] ^ clientSignature[i]);
    }
    if (!MessageDigest.isEqual(algorithm.hash(clientKey), credential.storedKey())) {
      throw new SaslAuthException("authentication failed");
    }
    byte[] serverSignature = algorithm.hmac(credential.serverKey(), authMessage);
    state = State.CONFIRM;
    return ("v=" + Base64.getEncoder().encodeToString(serverSignature))
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
