// (C) 2026 uchicom
package com.uchicom.smtp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** PLAIN / LOGIN認証機構のテスト. */
class PlainLoginMechanismTest {

  static final PasswordVerifier VERIFIER =
      (user, password) -> "alice".equals(user) && "secret".equals(password);

  static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void plain_success() throws Exception {
    var mechanism = new PlainMechanism(VERIFIER);
    mechanism.step(bytes("\0alice\0secret"));
    assertThat(mechanism.isComplete()).isTrue();
    assertThat(mechanism.getUser()).isEqualTo("alice");
  }

  @Test
  void plain_failure() {
    assertThatThrownBy(() -> new PlainMechanism(VERIFIER).step(bytes("\0alice\0wrong")))
        .isInstanceOf(SaslAuthException.class);
    assertThatThrownBy(() -> new PlainMechanism(VERIFIER).step(bytes("bob\0alice\0secret")))
        .isInstanceOf(SaslAuthException.class);
    assertThatThrownBy(() -> new PlainMechanism(VERIFIER).step(bytes("alice\0secret")))
        .isInstanceOf(SaslAuthException.class);
  }

  @Test
  void login_success() throws Exception {
    var mechanism = new LoginMechanism(VERIFIER);
    assertThat(mechanism.initialChallenge()).isEqualTo(bytes("Username:"));
    assertThat(mechanism.step(bytes("alice"))).isEqualTo(bytes("Password:"));
    assertThat(mechanism.isComplete()).isFalse();
    mechanism.step(bytes("secret"));
    assertThat(mechanism.isComplete()).isTrue();
    assertThat(mechanism.getUser()).isEqualTo("alice");
  }

  @Test
  void login_failure() throws Exception {
    var mechanism = new LoginMechanism(VERIFIER);
    mechanism.step(bytes("alice"));
    assertThatThrownBy(() -> mechanism.step(bytes("wrong"))).isInstanceOf(SaslAuthException.class);
    assertThat(mechanism.getUser()).isNull();
  }
}
