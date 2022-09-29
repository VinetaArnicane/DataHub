package com.datahub.plugins.auth.authentication;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;


/**
 * Class representing an authenticated actor accessing DataHub.
 */
public class Authentication {

  private final Actor authenticatedActor;
  private final String credentials;
  private final Map<String, Object> claims;

  public Authentication(@Nonnull final Actor authenticatedActor, @Nonnull final String credentials) {
    this(authenticatedActor, credentials, Collections.emptyMap());
  }

  public Authentication(@Nonnull final Actor authenticatedActor, @Nonnull final String credentials,
      @Nonnull final Map<String, Object> claims) {
    this.authenticatedActor = Objects.requireNonNull(authenticatedActor);
    this.credentials = Objects.requireNonNull(credentials);
    this.claims = Objects.requireNonNull(claims);
  }

  /**
   * Returns the authenticated actor
   */
  public Actor getActor() {
    return this.authenticatedActor;
  }

  /**
   * Returns the credentials associated with the current request (e.g. the value of the "Authorization" header)
   */
  public String getCredentials() {
    return this.credentials;
  }

  /**
   * Returns an arbitrary set of claims resolved by the Authenticator
   */
  public Map<String, Object> getClaims() {
    return this.claims;
  }
}
