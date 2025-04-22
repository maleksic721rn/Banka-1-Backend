package com.banka1.user.service;


import com.banka1.user.DTO.request.SetPasswordRequest;
import com.banka1.user.model.SetPassword;
import com.banka1.user.repository.CustomerRepository;
import com.banka1.user.repository.EmployeeRepository;
import com.banka1.user.repository.SetPasswordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class SetPasswordService {
	private final SetPasswordRepository setPasswordRepository;
	private final CustomerRepository customerRepository;
	private final EmployeeRepository employeeRepository;
	private final PasswordEncoder encoder;

	public SetPasswordService(SetPasswordRepository setPasswordRepository, CustomerRepository customerRepository,
	                          EmployeeRepository employeeRepository, PasswordEncoder encoder) {
		this.setPasswordRepository = setPasswordRepository;
		this.customerRepository = customerRepository;
		this.employeeRepository = employeeRepository;
		this.encoder = encoder;
	}

	public void saveSetPasswordRequest(String token, long userId, boolean customer) {
		var now = Instant.now();
		var setPassword = SetPassword.builder()
				.token(token)
				.userId(userId)
				.used(false)
				.createdDate(now)
				.expirationDate(Instant.now()
						.plus(Duration.ofDays(1)))
				.customer(customer)
				.build();
		setPasswordRepository.save(setPassword);
	}


	public void setPassword(SetPasswordRequest dto) {
		log.debug("SetPassword called");

		var setRequest = setPasswordRepository.findByToken(dto.getToken());
		if (setRequest.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token nije pronađen.");
		log.debug("Token valid");
		var setPassword = setRequest.get();
		if (setPassword.getExpirationDate()
				.isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token je istekao.");
		}
		log.debug("Token not expired");
		if (setPassword.getUsed()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token je već korišćen.");
		}
		var password = encoder.encode(dto.getPassword());
		if (setPassword.getCustomer()) {
			var customerOptional = customerRepository.findById(setPassword.getUserId());
			if (customerOptional.isEmpty())
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Korisnik nije pronađen.");
			var customer = customerOptional.get();
			customer.setPassword(password);
			customerRepository.save(customer);
		} else {
			var employeeOptional = employeeRepository.findById(setPassword.getUserId());
			if (employeeOptional.isEmpty())
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Korisnik nije pronađen.");
			var employee = employeeOptional.get();
			employee.setPassword(password);
			employeeRepository.save(employee);
		}
		setPassword.setUsed(true);
		setPasswordRepository.save(setPassword);
		log.debug("Password set");
	}
}