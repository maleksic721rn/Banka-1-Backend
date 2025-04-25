package com.banka1.user.aspect;

import com.banka1.common.model.Permission;
import com.banka1.common.model.Position;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotacija koja se vezuje za kontroler metode za koje je potrebna autorizacija.
 * Ako ijedna od potrebnih permisija nije zadovoljena ili korisnik nema nijednu od izlistanih pozicija, neće biti autorizovan.
 * Date metode moraju primati odvojen parametar koji sadrži autorizacioni header i <b>ima isto ime</b>.
 * <p></p>
 * Ako je <code>allowIdFallback</code> postavljeno kao <code>true</code>, u slučaju neuspele autorizacije preko permisija/pozicije omogućava se autorizacija preko id-a korisnika.
 * Ovo zahteva da korisnički id postoji kao deo request putanje, autorizovaće se ako se taj id poklapa sa onim u tokenu.
 * <p></p>
 * Primer korišćenja:
 * <p></p>
 * <pre>
 * {@code
 * @Authorization(permissions = { Permission.READ_EMPLOYEE }, positions = { Position.MANAGER })
 * @GetMapping(...)
 * public ResponseEntity<?> requireReadUserPerms(@RequestHeader("Authorization") String authorization, ...) {
 *     ...
 * }
 *
 * // Primer gde direktor može da menja sve korisnike, a korisnici samo sami sebe
 * @Authorization(positions = { Position.DIRECTOR }, allowIdFallback = true)
 * @PutMapping("/{id}/...")
 * public ResponseEntity<?> requireWriteUserPerms(@RequestHeader("Authorization") String authorization, @PathVariable("id") Long id, ...) {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Authorization {
    Permission[] permissions() default { };
    Position[] positions() default { };
    boolean allowIdFallback() default false;
}