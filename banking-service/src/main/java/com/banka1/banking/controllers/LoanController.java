package com.banka1.banking.controllers;
import com.banka1.banking.aspect.LoanAuthorization;
import com.banka1.banking.dto.request.CreateLoanDTO;
import com.banka1.banking.dto.request.LoanUpdateDTO;
import com.banka1.banking.models.Installment;
import com.banka1.banking.models.Loan;
import com.banka1.banking.repository.InstallmentsRepository;
import com.banka1.banking.repository.LoanRepository;
import com.banka1.banking.services.LoanService;
import com.banka1.banking.services.implementation.AuthService;
import com.banka1.banking.utils.ResponseMessage;
import com.banka1.banking.utils.ResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/loans")
@Tag(name = "Loan API", description = "API za pozive vezane za kredite i rate")
public class LoanController {
    @Autowired
    private LoanService loanService;
    @Autowired
    private AuthService authService;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private InstallmentsRepository installmentRepository;

    @PostMapping("/")
    @Operation(summary = "Kreiranje zahteva za kredit",
            description = "Dodaje novi kredit u sistem i cuva se u bazi pod statusom na cekanju.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kredit uspešno kreiran.\n"),
            @ApiResponse(responseCode = "403", description = "Nevalidni podaci")
    })
    @LoanAuthorization(customerOnlyOperation = true)
    public ResponseEntity<?> createLoan(@Valid @RequestBody CreateLoanDTO createLoanDTO) {
        Loan newLoan = null;
        try {
            newLoan = loanService.createLoan(createLoanDTO);
        } catch (RuntimeException e) {
            return ResponseTemplate.create(ResponseEntity.badRequest(), e);
        }

        if (newLoan == null) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.SEE_OTHER), false, null,
                    ResponseMessage.WRONG_NUM_OF_INSTALLMENTS.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("account_number", newLoan.getAccount().getId());
        response.put("message", "Kredit uspešno kreiran.\n");

        return ResponseTemplate.create(ResponseEntity.status(HttpStatus.CREATED), true, response, null);
    }

/// samo zaposleni imaju pristup
    @GetMapping("/pending")
    @Operation(summary = "Pregled svih kredita na cekanju",
            description = "Pregled svih kredita za koje su korisnici podneli zahtev a koji jos uvek nisu odobreni/odbijeni")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getPendingLoans() {
        try {
            List<Loan> loans = loanService.getPendingLoans();
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loans", loans), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    /// samo zaposleni imaju pristup
    @GetMapping("/admin/{user_id}")
    @Operation(summary = "Pregled svih kredita korisnika",
            description = "Pregled svih kredita koje korisnik ima na racunima")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getAllUserLoans(
            @PathVariable("user_id") Long userId) {
        try {
            List<Loan> loans = loanService.getAllUserLoans(userId);
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loans", loans), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    @GetMapping("/")
    @Operation(summary = "Pregled svih kredita jednog korisnika",
            description = "Pregled svih kredita koje korisnik ima vezane za svoje racune")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    public ResponseEntity<?> getLoansByUserId(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Long ownerId = authService.parseToken(authService.getToken(authorization)).get("id", Long.class);
            List<Loan> loans = loanService.getAllUserLoans(ownerId);

            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loans", loans), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }
/// samo zaposleni
    @GetMapping("/admin")
    @Operation(summary = "Pregled svih kredita jednog korisnika",
            description = "Pregled svih kredita koje korisnik ima vezane za svoje racune")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getAllLoans(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            List<Loan> loans = loanService.getAllLoans();
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loans", loans), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }


    @GetMapping("/{loan_id}")
    @Operation(summary = "Pregled detalja kredita korisnika",
            description = "Pregled detalja jednog od kredita korisnika")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    @LoanAuthorization
    public ResponseEntity<?> getLoanDetails(@PathVariable("loan_id") Long loanId) {
        try {
            Loan loan = loanService.getLoanDetails(loanId);
            if (loan == null) {
                return ResponseTemplate.create(ResponseEntity.status(HttpStatus.NOT_FOUND), false, null,
                        ResponseMessage.NOT_THE_OWNER.toString());
            }
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loan", loan), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    @GetMapping("/admin/{user_id}/{loan_id}")
    @Operation(summary = "Pregled detalja kredita korisnika",
            description = "Pregled detalja jednog od kredita korisnika")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "nema kredita na cekanju.")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getLoanDetails(
            @PathVariable("user_id") Long userId,
            @PathVariable("loan_id") Long loanId) {
        try {
            Loan loan = loanService.getLoanDetails(loanId);
            if (loan == null) {
                return ResponseTemplate.create(ResponseEntity.status(HttpStatus.NOT_FOUND), false, null, "Kredit sa ID-em " + loanId + " nije pronađen."
                       );
            }
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("loan", loan), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

/// samo zaposleni ima pristup
    @PutMapping("/admin/{loan_id}/approve")
    @Operation(summary = "Ažuriranje zahteva za kredit od strane zaposlenog",
            description = "Omogućava zaposlenima da ažuriraju stanje kredita.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kredit uspešno ažuriran"),
            @ApiResponse(responseCode = "404", description = "Kredit nije pronađen"),
            @ApiResponse(responseCode = "400", description = "Nevalidni podaci za ažuriranje")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> updateLoanRequest(
            @PathVariable("loan_id") Long loanId,
            @RequestBody LoanUpdateDTO loanUpdateDTO) {
        try {
            // Validate required fields
            if (loanUpdateDTO == null || loanUpdateDTO.getApproved() == null) {
                return ResponseTemplate.create(ResponseEntity.badRequest(), false,
                        null, "Approved status must be provided");
            }
            
            Loan updatedLoan = loanService.updateLoanRequest(loanId, loanUpdateDTO);
            if (updatedLoan == null) {
                return ResponseTemplate.create(ResponseEntity.badRequest(), false,
                        null, ResponseMessage.LOAN_NOT_FOUND.getMessage());
            }
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true,
                    Map.of("message", ResponseMessage.UPDATED, "data", updatedLoan), null);
        } catch (HttpMessageNotReadableException e) {
            // Specific handling for JSON parsing errors
            return ResponseTemplate.create(ResponseEntity.badRequest(), false,
                    null, "Failed to parse request body. Please check JSON format.");
        } catch (RuntimeException e) {
            return ResponseTemplate.create(ResponseEntity.badRequest(), e);
        }
    }
    @GetMapping("/admin/{user_id}/installments")
    @Operation(summary = "Pregled svih rata korisnika",
            description = "Pregled svih rata za sve kredite korisnika")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista rata korisnika."),
            @ApiResponse(responseCode = "404", description = "Nema pronađenih rata za korisnika.")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getUserInstallments(@PathVariable("user_id") Long userId) {
        try {
            List<Installment> installments = loanService.getUserInstallments(userId);
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("installments", installments), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    @GetMapping("/installments")
    @Operation(summary = "Pregled svih rata korisnika",
            description = "Pregled svih rata koje su vezane za kredite korisnika")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista rata korisnika."),
            @ApiResponse(responseCode = "404", description = "Nema pronađenih rata za korisnika.")
    })
    public ResponseEntity<?> getUserInstallments(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Long userId = authService.parseToken(authService.getToken(authorization)).get("id", Long.class);
            List<Installment> installments = loanService.getUserInstallments(userId);
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("installments", installments), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    @GetMapping("/{loan_id}/remaining_installments")
    @Operation(summary = "Broj preostalih rata za kredit",
            description = "broj ukupnih rata - broj placenih rata")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "lista kredita."),
            @ApiResponse(responseCode = "404", description = "Korisnik nije vlasnik kredita ili kredit ne postoji.")
    })
    public ResponseEntity<?> getRemainingInstallments(
            @PathVariable("loan_id") Long loanId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Long ownerId = authService.parseToken(authService.getToken(authorization)).get("id", Long.class);
            Integer num = loanService.calculateRemainingInstallments(ownerId, loanId);
            if (num == null) {
                return ResponseTemplate.create(ResponseEntity.status(HttpStatus.NOT_FOUND), false, null,
                        ResponseMessage.NOT_THE_OWNER.toString());
            }
            // Ako je broj preostalih rata 0, vraćamo HTTP 200 OK sa praznom listom
            if (num == 0) {
                return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("remaining_number", 0), "Sve rate su plaćene.");
            }

            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("remaining_number", num), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, e.getMessage());
        }
    }

    @GetMapping("/start_cron")
    @Operation(summary = "Vestacko pokretanje cron joba")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "izvrsen"),
            @ApiResponse(responseCode = "404", description = "nije")
    })
    @LoanAuthorization(employeeOnlyOperation = true)
    public ResponseEntity<?> getRemainingInstallments() {
        try {
            loanService.processLoanPayments();
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("cron", "success"), null);
        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.badRequest(), false, null, e.getMessage());
        }
    }

}
