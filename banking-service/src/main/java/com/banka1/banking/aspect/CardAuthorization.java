package com.banka1.banking.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotacija koja se vezuje za kontroler metode za koje je potrebna autorizacija operacije vezane za kartice.
 * Ako korisnik koji šalje zahtev nema pristup računu za koji je vezana kartica kao korisnik i nije zaposlen, neće biti autorizovan.
 * Date metode mogu primati različite parametre koji sadrže podatke o operaciji (ID kartice ili računa).
 * <p></p>
 * Ako je <code>customerOnlyOperation</code> postavljeno kao <code>true</code>, zaposleni neće imati pristup ovoj metodi.
 * <p></p>
 * Ako je <code>employeeOnlyOperation</code> postavljeno kao <code>true</code>, mušterije neće imati pristup ovoj metodi.
 * <p></p>
 * Ako je <code>disallowAdminFallback</code> postavljeno kao <code>true</code>, administratori neće imati pristup ovoj metodi.
 * <p></p>
 * Primer korišćenja:
 * <p></p>
 * <pre>
 * {@code
 * @AccountAuthorization
 * @GetMapping("/{card_id}/...")
 * public ResponseEntity<?> requireAccountAccess(@RequestHeader("Authorization") String authorization, @PathVariable("card_id") Long cardId ...) {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CardAuthorization {
    boolean customerOnlyOperation() default false;
    boolean employeeOnlyOperation() default false;
    boolean disallowAdminFallback() default false;
}
