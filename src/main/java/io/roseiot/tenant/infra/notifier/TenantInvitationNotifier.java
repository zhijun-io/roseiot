package io.roseiot.tenant.infra.notifier;

public interface TenantInvitationNotifier {

    void sendInvitation(String email, String targetRole, String plainToken);
}
