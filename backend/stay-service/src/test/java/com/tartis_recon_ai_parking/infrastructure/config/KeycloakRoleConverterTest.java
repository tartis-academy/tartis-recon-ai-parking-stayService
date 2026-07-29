package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    @DisplayName("Debe mapear los roles de realm_access.roles a authorities con prefijo ROLE_")
    void shouldMapRealmRolesToRoleAuthorities() {
        Jwt jwt = buildJwt(Map.of("roles", List.of("ADMIN", "OPERARIO")));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OPERARIO");
    }

    @Test
    @DisplayName("Debe devolver una coleccion vacia si el token no trae el claim realm_access")
    void shouldReturnEmptyWhenRealmAccessClaimIsMissing() {
        Jwt jwt = buildJwt(null);

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Debe devolver una coleccion vacia si realm_access no trae la lista de roles")
    void shouldReturnEmptyWhenRolesListIsMissing() {
        Jwt jwt = buildJwt(Map.of());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Debe devolver una coleccion vacia si roles no es una coleccion (ej. llega como String suelto)")
    void shouldReturnEmptyWhenRolesClaimIsNotACollection() {
        Jwt jwt = buildJwt(Map.of("roles", "ADMIN"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Debe ignorar elementos nulos, en blanco o de tipo distinto a String dentro de la lista de roles")
    void shouldFilterOutInvalidRoleEntries() {
        List<Object> rolesWithNoise = Arrays.asList("ADMIN", null, "", "   ", 42, "OPERARIO");
        Jwt jwt = buildJwt(Map.of("roles", rolesWithNoise));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OPERARIO");
    }

    private Jwt buildJwt(Map<String, Object> realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("sub", "test-user");

        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }

        return builder.build();
    }
}
