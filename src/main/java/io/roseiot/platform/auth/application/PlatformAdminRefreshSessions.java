package io.roseiot.platform.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.auth.domain.TokenDigests;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import io.roseiot.platform.support.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminRefreshSessions {

    private final PlatformAdminRefreshSessionMapper refreshSessionMapper;
    private final JwtEncoder jwtEncoder;

    public PlatformAdminRefreshSessions(
            PlatformAdminRefreshSessionMapper refreshSessionMapper, JwtEncoder jwtEncoder) {
        this.refreshSessionMapper = refreshSessionMapper;
        this.jwtEncoder = jwtEncoder;
    }

    PlatformAdminSessionService.SessionTokens issueNewFamily(UUID platformAdminId, Instant now) {
        UUID sessionId = UuidV7.generate();
        UUID familyId = UuidV7.generate();
        String refreshToken = TokenDigests.generateUrlSafe256Bit();
        insertSession(sessionId, platformAdminId, familyId, refreshToken, now);
        return new PlatformAdminSessionService.SessionTokens(issueAccessToken(platformAdminId, sessionId), refreshToken);
    }

    PlatformAdminSessionService.SessionTokens rotate(PlatformAdminRefreshSession current, Instant now) {
        current.setRevokedAt(now);
        refreshSessionMapper.updateById(current);

        UUID newSessionId = UuidV7.generate();
        String refreshToken = TokenDigests.generateUrlSafe256Bit();
        insertSession(newSessionId, current.getPlatformAdminId(), current.getFamilyId(), refreshToken, now);
        return new PlatformAdminSessionService.SessionTokens(
                issueAccessToken(current.getPlatformAdminId(), newSessionId), refreshToken);
    }

    void revokeSession(UUID sessionId, Instant revokedAt) {
        if (sessionId == null) {
            return;
        }
        PlatformAdminRefreshSession session = refreshSessionMapper.selectById(sessionId);
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    void revokeAllForAdmin(UUID platformAdminId, Instant revokedAt) {
        revokeSessions(refreshSessionMapper.selectList(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getPlatformAdminId, platformAdminId)
                        .isNull(PlatformAdminRefreshSession::getRevokedAt)), revokedAt);
    }

    void revokeFamily(UUID familyId, Instant revokedAt) {
        revokeSessions(refreshSessionMapper.selectList(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getFamilyId, familyId)
                        .isNull(PlatformAdminRefreshSession::getRevokedAt)), revokedAt);
    }

    private void insertSession(
            UUID sessionId, UUID platformAdminId, UUID familyId, String plainRefreshToken, Instant now) {
        PlatformAdminRefreshSession session = new PlatformAdminRefreshSession();
        session.setId(sessionId);
        session.setPlatformAdminId(platformAdminId);
        session.setFamilyId(familyId);
        session.setTokenHash(TokenDigests.sha256Hex(plainRefreshToken));
        session.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        session.setCreatedAt(now);
        refreshSessionMapper.insert(session);
    }

    private void revokeSessions(List<PlatformAdminRefreshSession> activeSessions, Instant revokedAt) {
        for (PlatformAdminRefreshSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    private String issueAccessToken(UUID platformAdminId, UUID sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(platformAdminId.toString())
                .claim("sid", sessionId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
