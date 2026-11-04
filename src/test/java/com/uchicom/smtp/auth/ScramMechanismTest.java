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

  /** クライアント側の処理. 最後まで進めた機構を返す. */
  static ScramMechanism run(ScramAlgorithm algorithm, String serverPassword, String password)
      throws Exception {
    var mechanism = new ScramMechanism(algorithm, source(serverPassword));
    var clientFirstBare = "n=alice,r=clientnonce";
    var serverFirst = new String(mechanism.step(bytes("n,," + clientFirstBare)));
    var attrs = serverFirst.split(",");
    var nonce = attrs[0].substring(2);
    var salt = Base64.getDecoder().decode(attrs[1].substring(2));
    var iterations = Integer.parseInt(attrs[2].substring(2));
    assertThat(nonce).startsWith("clientnonce");

    var credential = algorithm.derive(password, salt, iterations);
    var saltedPassword = algorithm.hi(bytes(password), salt, iterations);
    var clientKey = algorithm.hmac(saltedPassword, bytes("Client Key"));
    var withoutProof = "c=biws,r=" + nonce;
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
