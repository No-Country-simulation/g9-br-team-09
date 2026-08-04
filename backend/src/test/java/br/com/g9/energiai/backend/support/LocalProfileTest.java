package br.com.g9.energiai.backend.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience",
        "jwt.access-token-expiration=15m"
})
public @interface LocalProfileTest {
}
