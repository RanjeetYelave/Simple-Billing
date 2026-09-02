package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.BusinessLetter;
import com.billing.simple.billsoft.entities.LetterRecipientType;
import com.billing.simple.billsoft.entities.LetterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessLetterRepository extends JpaRepository<BusinessLetter, Long> {

    List<BusinessLetter> findByFirmIdOrderByLetterDateDescIdDesc(Long firmId);

    Optional<BusinessLetter> findByIdAndFirmId(Long id, Long firmId);

    List<BusinessLetter> findByFirmIdAndRecipientTypeOrderByLetterDateDescIdDesc(Long firmId, LetterRecipientType recipientType);

    List<BusinessLetter> findByFirmIdAndPartyIdOrderByLetterDateDescIdDesc(Long firmId, Long partyId);

    List<BusinessLetter> findByFirmIdAndCustomerIdOrderByLetterDateDescIdDesc(Long firmId, Long customerId);

    List<BusinessLetter> findByFirmIdAndStatusOrderByLetterDateDescIdDesc(Long firmId, LetterStatus status);

    List<BusinessLetter> findByFirmIdAndLetterDateBetweenOrderByLetterDateDescIdDesc(Long firmId, LocalDate start, LocalDate end);

    long countByFirmId(Long firmId);
}
