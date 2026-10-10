package io.roseiot.tenant.infra.notifier;

import io.roseiot.system.application.SystemSettingService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingTenantInvitationNotifierTest {

    @Test
    void readsGeneralBaseUrlFromSystemSettingsWhenCreatingInvitationLink() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.requireGeneralBaseUrl()).thenReturn("https://roseiot.example");
        LoggingTenantInvitationNotifier notifier = new LoggingTenantInvitationNotifier(settings);

        notifier.sendInvitation("admin@roseiot.example", "Administrator", "invite-token");

        verify(settings).requireGeneralBaseUrl();
    }
}
