package io.roseiot.tenant.infra.invitation;

public interface TenantInvitationNotifier {

    void sendInvitation(String email, String targetRole, String plainToken);
}
