package org.oao.eticket.application.domain.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.oao.eticket.application.domain.model.*;
import org.oao.eticket.application.port.in.CreateAuthTokenUseCase;
import org.oao.eticket.application.port.out.SaveAuthTokenMetadataCommand;
import org.oao.eticket.application.port.out.SaveAuthTokenMetadataPort;
import org.oao.eticket.common.Pair;
import org.oao.eticket.common.annotation.UseCase;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@UseCase
public class CreateAuthTokenService implements CreateAuthTokenUseCase {

  private final SaveAuthTokenMetadataPort saveAuthTokenMetadataPort;
  private final Algorithm cryptoAlgorithm;
  private final String issuer;
  private final Duration accessTokenLifetime;
  private final Duration refreshTokenLifetime;

  private CreateAuthTokenService(
      final SaveAuthTokenMetadataPort saveAuthTokenMetadataPort,
      final Algorithm cryptoAlgorithm,
      @Value("${eticket.auth.jwt.issuer}") final String issuer,
      @Value("${eticket.auth.jwt.access-token-lifetime}") final int accessTokenLifetime,
      @Value("${eticket.auth.jwt.refresh-token-lifetime}") final int refreshTokenLifetime) {
    this.saveAuthTokenMetadataPort = saveAuthTokenMetadataPort;
    this.cryptoAlgorithm = cryptoAlgorithm;
    this.issuer = issuer;
    this.accessTokenLifetime = Duration.ofSeconds(accessTokenLifetime);
    this.refreshTokenLifetime = Duration.ofSeconds(refreshTokenLifetime);
  }

  @Override
  public Pair<String, String> create(final User targetUser) {
    final var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final var accessTokenId = AuthTokenId.of(UUID.randomUUID());
    final var refreshTokenId = AuthTokenId.of(UUID.randomUUID());

    final var accessJWT =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(issuer)
            .withSubject(targetUser.getId().toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(accessTokenLifetime))
            .withClaim("tid", accessTokenId.toString())
            .withClaim("aut", List.of(targetUser.getRole().getRoleName()))
            .sign(cryptoAlgorithm);

    final var refreshJWT =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(issuer)
            .withSubject(targetUser.getId().toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(refreshTokenLifetime))
            .withClaim("tid", refreshTokenId.toString())
            .withClaim("aid", accessTokenId.toString())
            .sign(cryptoAlgorithm);

    final var accessTokenMetadata = new AccessTokenMetadata(accessTokenId, signatureOf(accessJWT));
    final var refreshTokenMetadata =
        new RefreshTokenMetadata(
            refreshTokenId, accessTokenId, signatureOf(accessJWT), signatureOf(refreshJWT));

    saveAuthTokenMetadataPort.save(
        new SaveAuthTokenMetadataCommand(
            targetUser.getId(),
            accessTokenMetadata,
            accessTokenLifetime,
            refreshTokenMetadata,
            refreshTokenLifetime));

    return Pair.of(accessJWT, refreshJWT);
  }

  private String signatureOf(final String jwt) {
    return jwt.substring(jwt.lastIndexOf('.') + 1);
  }
}
