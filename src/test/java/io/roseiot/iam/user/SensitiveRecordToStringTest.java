package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveRecordToStringTest {

    @Test
    void doesNotExposeCredentialsOrResetTokensFromSensitiveRecords() {
        var values = java.util.List.of(
                new PasswordResetRequestedEvent("person@example.test", "reset-token").toString(),
                new UserController.ChangePasswordRequest("current-password", "new-password").toString(),
                new UserController.ResetPasswordRequest("person@example.test").toString(),
                new UserController.CreateUserRequest("person@example.test", "Test Person", "new-password", 9L)
                        .toString(),
                new UserController.ResetTokenRequest("reset-token").toString(),
                new UserController.ConfirmPasswordResetRequest("reset-token", "new-password").toString());

        assertThat(values)
                .allSatisfy(value -> assertThat(value)
                        .doesNotContain(
                                "person@example.test",
                                "current-password",
                                "new-password",
                                "encoded-password",
                                "reset-token"));
    }
}
