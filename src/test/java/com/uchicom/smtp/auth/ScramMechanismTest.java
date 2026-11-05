// (C) 2026 uchicom
package com.uchicom.smtp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** SCRAM認証機構のテスト. */
class ScramMechanismTest {

  static final byte[] SALT = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

  static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  static ScramCredentialSource source(String password) {
    return (user, algorithm) -> algorithm.derive(password, SALT, 4096);
  }

  static ScramMechanism run(ScramAlgorithm algorithm, String serverPassword, String password)
      throws Exception {
    return run(
        new ScramMechanism(algorithm, source(serverPassword)),
        algorithm,
        password,
        "n,,",
        new byte[0]);
  }

  /**
   * クライアント側の処理. 最後まで進めた機構を返す.
   *
   * @param gs2Header クライアントが送るgs2ヘッダ(例: "n,,", "p=tls-server-end-point,,")
   * @param clientBinding クライアントが最終メッセージのc=に付加するバインディングデータ
   */
  static ScramMechanism run(
      ScramMechanism mechanism,
      ScramAlgorithm algorithm,
      String password,
      String gs2Header,
      byte[] clientBinding)
      throws Exception {
    var clientFirstBare = "n=alice,r=clientnonce";
    var serverFirst = new String(mechanism.step(bytes(gs2Header + clientFirstBare)));
    var attrs = serverFirst.split(",");
    var nonce = attrs[0].substring(2);
    var salt = Base64.getDecoder().decode(attrs[1].substring(2));
    var iterations = Integer.parseInt(attrs[2].substring(2));
    assertThat(nonce).startsWith("clientnonce");

    var credential = algorithm.derive(password, salt, iterations);
    var saltedPassword = algorithm.hi(bytes(password), salt, iterations);
    var clientKey = algorithm.hmac(saltedPassword, bytes("Client Key"));
    var cbind = new java.io.ByteArrayOutputStream();
    cbind.writeBytes(bytes(gs2Header));
    cbind.writeBytes(clientBinding);
    var withoutProof =
        "c=" + Base64.getEncoder().encodeToString(cbind.toByteArray()) + ",r=" + nonce;
    var authMessage = bytes(clientFirstBare + "," + serverFirst + "," + withoutProof);
    var signature = algorithm.hmac(credential.storedKey(), authMessage);
    var proof = new byte[clientKey.length];
    for (int i = 0; i < proof.length; i++) {
      proof[i] = (byte) (clientKey[i] ^ signature[i]);
    }
    var serverFinal =
        mechanism.step(bytes(withoutProof + ",p=" + Base64.getEncoder().encodeToString(proof)));
    var expected = algorithm.hmac(credential.serverKey(), authMessage);
    assertThat(new String(serverFinal))
        .isEqualTo("v=" + Base64.getEncoder().encodeToString(expected));
    assertThat(mechanism.isComplete()).isFalse();
    mechanism.step(new byte[0]);
    return mechanism;
  }

  @Test
  void success_allAlgorithms() throws Exception {
    for (var algorithm : ScramAlgorithm.values()) {
      var mechanism = run(algorithm, "secret", "secret");
      assertThat(mechanism.isComplete()).isTrue();
      assertThat(mechanism.getUser()).isEqualTo("alice");
    }
  }

  @Test
  void success_nonAsciiPassword() throws Exception {
    assertThat(run(ScramAlgorithm.SHA_256, "パスワード", "パスワード").isComplete()).isTrue();
  }

  @Test
  void wrongPassword() {
    assertThatThrownBy(() -> run(ScramAlgorithm.SHA_256, "secret", "wrong"))
        .isInstanceOf(SaslAuthException.class);
  }

  @Test
  void channelBindingRequest_isRejected() {
    var mechanism = new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"));
    assertThatThrownBy(() -> mechanism.step(bytes("p=tls-unique,,n=alice,r=abc")))
        .isInstanceOf(SaslAuthException.class);
  }

  static final byte[] BINDING = {10, 20, 30, 40};
  static final String PLUS_GS2 = "p=tls-server-end-point,,";

  static ScramMechanism plus() {
    return new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"), true, BINDING);
  }

  @Test
  void plus_success() throws Exception {
    for (var algorithm : ScramAlgorithm.values()) {
      var mechanism = new ScramMechanism(algorithm, source("secret"), true, BINDING);

      run(mechanism, algorithm, "secret", PLUS_GS2, BINDING);

      assertThat(mechanism.isComplete()).isTrue();
      assertThat(mechanism.getUser()).isEqualTo("alice");
    }
  }

  @Test
  void plus_bindingMismatch_isRejected() {
    assertThatThrownBy(
            () -> run(plus(), ScramAlgorithm.SHA_256, "secret", PLUS_GS2, new byte[] {1, 2}))
        .isInstanceOf(SaslAuthException.class)
        .hasMessage("channel binding mismatch");
  }

  @Test
  void plus_withoutBindingData_isRejected() {
    assertThatThrownBy(() -> run(plus(), ScramAlgorithm.SHA_256, "secret", PLUS_GS2, new byte[0]))
        .isInstanceOf(SaslAuthException.class)
        .hasMessage("channel binding mismatch");
  }

  @Test
  void plus_requiresServerEndPointType() {
    for (var flag : new String[] {"n", "y", "p=tls-unique", "p=tls-exporter", "p=other"}) {
      var mechanism = plus();
      assertThatThrownBy(() -> mechanism.step(bytes(flag + ",,n=alice,r=abc")))
          .isInstanceOf(SaslAuthException.class)
          .hasMessage("channel binding required");
    }
  }

  @Test
  void plus_requiresBindingData() {
    assertThatThrownBy(
            () -> new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"), true, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPlus_rejectsChannelBindingType() {
    var mechanism = new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"), false, BINDING);
    assertThatThrownBy(() -> mechanism.step(bytes(PLUS_GS2 + "n=alice,r=abc")))
        .isInstanceOf(SaslAuthException.class)
        .hasMessage("channel binding not supported");
  }

  /** サーバーが-PLUSを広告している場合、クライアントのy(対応を知らない)はダウングレードとして拒否する. */
  @Test
  void nonPlus_downgradeIsRejectedWhenPlusIsAdvertised() {
    var mechanism = new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"), false, BINDING);
    assertThatThrownBy(() -> mechanism.step(bytes("y,,n=alice,r=abc")))
        .isInstanceOf(SaslAuthException.class)
        .hasMessage("channel binding downgrade detected");
  }

  @Test
  void nonPlus_noBinding_isAcceptedWhenPlusIsAdvertised() throws Exception {
    var mechanism = new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"), false, BINDING);

    run(mechanism, ScramAlgorithm.SHA_256, "secret", "n,,", new byte[0]);

    assertThat(mechanism.isComplete()).isTrue();
  }

  /** -PLUSを広告していない場合は、クライアントのyを受け付ける. */
  @Test
  void nonPlus_yIsAcceptedWhenPlusIsNotAdvertised() throws Exception {
    var mechanism = new ScramMechanism(ScramAlgorithm.SHA_256, source("secret"));

    run(mechanism, ScramAlgorithm.SHA_256, "secret", "y,,", new byte[0]);

    assertThat(mechanism.isComplete()).isTrue();
  }

  @Test
  void find_plusAndSha3_areUnsupported() {
    assertThat(ScramAlgorithm.find("scram-sha-256")).isEqualTo(ScramAlgorithm.SHA_256);
    assertThat(ScramAlgorithm.find("SCRAM-SHA-256-PLUS")).isNull();
    assertThat(ScramAlgorithm.find("SCRAM-SHA3-512")).isNull();
  }

  /** RFC 7677 の例(SCRAM-SHA-256)で資格情報の導出を検証する. */
  @Test
  void rfc7677_vector() {
    var algorithm = ScramAlgorithm.SHA_256;
    var salt = Base64.getDecoder().decode("W22ZaJ0SNY7soEsUEjb6gQ==");
    var credential = algorithm.derive("pencil", salt, 4096);
    var authMessage =
        bytes(
            "n=user,r=rOprNGfwEbeRWgbNEkqO"
                + ",r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096"
                + ",c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0");

    var serverSignature = algorithm.hmac(credential.serverKey(), authMessage);

    assertThat(Base64.getEncoder().encodeToString(serverSignature))
        .isEqualTo("6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=");
  }
}
