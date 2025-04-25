package com.banka1.banking.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotacija koja se vezuje za kontroler metode za koje je potrebna autorizacija operacije vezane za primaoce.
 * Ako korisnik koji šalje zahtev nema pristup računu za koji je primalac vezan, neće biti autorizovan.
 * Date metode mogu primati različite parametre koji sadrže podatke o operaciji (ID računa ili primaoca).
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
 * @GetMapping("/{receiver_id}/...")
 * public ResponseEntity<?> requireAccountAccess(@RequestHeader("Authorization") String authorization, @PathVariable("receiver_id") Long receiverId ...) {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReceiverAuthorization {
    boolean customerOnlyOperation() default false;
    boolean employeeOnlyOperation() default false;
    boolean disallowAdminFallback() default false;
}
