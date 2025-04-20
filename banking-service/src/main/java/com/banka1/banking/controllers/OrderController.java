package com.banka1.banking.controllers;

import com.banka1.banking.dto.OrderTransactionInitiationDTO;
import com.banka1.banking.services.OrderService;

import com.banka1.banking.utils.ResponseTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderService orderService;

	public record OrderDTO(String direction, Long accountId, Long userId, Double amount, Double fee) {}

    @PostMapping("/execute/")
//    @PreAuthorize("hasAuthority('SCOPE_trading-service')")
    public ResponseEntity<?> executeOrder(@RequestBody OrderDTO order) {
        try {
            String direction = order.direction;
            Long accountId = order.accountId;
            Long userId = order.userId;
            Double amount = order.amount;
            Double fee = order.fee;

            if(direction == null)
                throw new Exception();

            Double finalAmount = orderService.executeOrder(direction, userId, accountId, amount, fee);

            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.OK), true, Map.of("finalAmount", finalAmount), null);
        } catch (IllegalArgumentException e) {
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.FORBIDDEN), false, null, "Nedovoljna sredstva");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST), false, null, "Nevalidni podaci");
        }
    }
	
	public record InitiationDTO(String uid, Long buyerAccountId, Long sellerAccountId, Double amount) {}

    @PostMapping("/initiate/")
//    @PreAuthorize("hasAuthority('SCOPE_trading-service')")
    public ResponseEntity<?> initiateOrderTransaction(@RequestBody InitiationDTO initiationDto) {
        try {
            String uid = initiationDto.uid;
            Long buyerAccountId = initiationDto.buyerAccountId;
            Long sellerAccountId = initiationDto.sellerAccountId;
            Double amount = initiationDto.amount;

            OrderTransactionInitiationDTO dto = new OrderTransactionInitiationDTO();
            dto.setUid(uid);
            dto.setBuyerAccountId(buyerAccountId);
            dto.setSellerAccountId(sellerAccountId);
            dto.setAmount(amount);

            orderService.processOrderTransaction(dto);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nevalidni podaci u tokenu za initiate transakciju");
        }
    }

}
