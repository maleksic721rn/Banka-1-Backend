package com.banka1.banking.services;

import com.banka1.banking.dto.ReceiverDTO;
import com.banka1.banking.models.Receiver;
import com.banka1.banking.repository.ReceiverRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReceiverService {

    private final ReceiverRepository receiverRepository;

    public Receiver findById(Long receiverId) {
        return receiverRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Primalac sa ID-jem " + receiverId + " nije pronađen"));
    }

    public Receiver createReceiver(ReceiverDTO receiverDTO){

        if (receiverRepository.existsByCustomerIdAndAccountNumber(
                receiverDTO.getCustomerId(), receiverDTO.getAccountNumber())) {
            throw new IllegalArgumentException("Primalac sa ovim brojem računa već postoji za datog korisnika.");
        }

        Receiver receiver = new Receiver();

        receiver.setCustomerId(receiverDTO.getCustomerId());
        receiver.setAccountNumber(receiverDTO.getAccountNumber());

        String[] fullName = receiverDTO.getFullName().trim().split("\\s+",2);
        receiver.setFirstName(fullName[0]);
        receiver.setLastName(fullName.length > 1 ? fullName[1] : "");

        receiver.setAddress(receiverDTO.getAddress());

        return receiverRepository.save(receiver);
    }

    public List<Receiver> getReceiversByCustomerId(Long customerId){
        return receiverRepository.findByCustomerIdOrderByUsageCountDesc(customerId);
    }

    public Receiver updateReceiver(Long id,ReceiverDTO receiverDTO){
        Optional<Receiver> optionalReceiver = receiverRepository.findById(id);

        if(optionalReceiver.isEmpty()){
            throw new IllegalArgumentException("Primalac sa ID " + id + " ne postoji.");
        }

        Receiver receiver = optionalReceiver.get();

        if(receiverDTO.getAddress() != null)
             receiver.setAccountNumber(receiverDTO.getAccountNumber());

        if(receiverDTO.getFullName() != null){
            String[] fullName = receiverDTO.getFullName().trim().split("\\s+",2);
            receiver.setFirstName(fullName[0]);
            receiver.setLastName(fullName.length > 1 ? fullName[1] : "");
        }

        if(receiverDTO.getAddress() != null)
             receiver.setAddress(receiverDTO.getAddress());

        return receiverRepository.save(receiver);
    }

    public void deleteReceiver(Long id) {
        if (!receiverRepository.existsById(id)) {
            throw new IllegalArgumentException("Primalac sa ID " + id + " ne postoji.");
        }
        receiverRepository.deleteById(id);
    }

    /**
    *  The naming of this method is a lie; it checks whether the receiver exists for the given customer ID
    * @param id the customer ID
    * @return true if the receiver exists for the given customer ID, false otherwise
    */
    public boolean accountExists(Long id){
        return receiverRepository.existsByCustomerId(id);
    }


    public void incrementUsage(Long receiverId) {
        Receiver receiver = receiverRepository.findById(receiverId)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));
        receiver.setUsageCount(receiver.getUsageCount() + 1);
        receiverRepository.save(receiver);
    }

}
