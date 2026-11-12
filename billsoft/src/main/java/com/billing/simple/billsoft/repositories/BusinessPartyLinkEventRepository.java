package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.BusinessPartyLinkEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessPartyLinkEventRepository extends JpaRepository<BusinessPartyLinkEvent, Long> {

    List<BusinessPartyLinkEvent> findByFirmId(Long firmId);

    List<BusinessPartyLinkEvent> findByFirmIdAndLinkIdOrderByTimestampDesc(Long firmId, Long linkId);

    List<BusinessPartyLinkEvent> findByFirmIdOrderByTimestampDesc(Long firmId, Pageable pageable);

    List<BusinessPartyLinkEvent> findByFirmIdOrderByTimestampDesc(Long firmId);
}

