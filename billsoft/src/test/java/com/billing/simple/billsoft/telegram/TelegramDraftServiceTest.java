package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import com.billing.simple.billsoft.telegram.repo.TelegramDocumentDeliveryRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftItemRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramDraftRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TelegramDraftServiceTest {

    @Autowired
    private TelegramDraftService draftService;

    @Autowired
    private TelegramDraftRepository draftRepo;

    @Autowired
    private TelegramDraftItemRepository itemRepo;

    @Autowired
    private TelegramDocumentDeliveryRepository deliveryRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    private Long testFirmId = 1L;
    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        deliveryRepo.deleteAll();
        itemRepo.deleteAll();
        draftRepo.deleteAll();

        TenantContext.setCurrentFirmId(testFirmId);

        testCustomer = new Customer();
        testCustomer.setName("Raman Hardware");
        testCustomer.setPhone("9820011223");
        testCustomer.setFirmId(testFirmId);
        testCustomer = customerRepo.save(testCustomer);

        testProduct = new Product();
        testProduct.setName("PVC Pipe 12ft Supreme");
        testProduct.setPrice(BigDecimal.valueOf(275.00));
        testProduct.setUnit("Piece");
        testProduct.setFirmId(testFirmId);
        testProduct.setInInventory(true);
        testProduct.setStockQuantity(BigDecimal.valueOf(100));
        testProduct = productRepo.save(testProduct);
    }

    @Test
    void testCompleteGuidedBillingWorkflowAndIdempotency() {
        Long chatId = 1002L;
        Long userId = 998877L;

        // 1. Start draft
        TelegramDraft draft = draftService.startDraft(chatId, userId, testFirmId, "INVOICE");
        assertNotNull(draft.getDraftId());
        assertEquals("BUILDING", draft.getDraftState());
        assertEquals(1, draft.getVersion());

        // 2. Set customer
        draftService.setCustomer(draft.getDraftId(), testCustomer.getId(), testCustomer.getName(), testCustomer.getPhone());
        draft = draftService.getDraft(draft.getDraftId()).orElseThrow();
        assertEquals(testCustomer.getId(), draft.getCustomerId());
        assertEquals(2, draft.getVersion());

        // 3. Add line item
        draftService.addItem(draft.getDraftId(), testProduct.getId(), testProduct.getName(), "1 inch",
                testProduct.getUnit(), BigDecimal.valueOf(5), BigDecimal.valueOf(270.00), BigDecimal.ZERO);
        draft = draftService.getDraft(draft.getDraftId()).orElseThrow();
        assertEquals(3, draft.getVersion());

        // 4. Preview invoice (non-persisting calculation check)
        Invoice preview = draftService.previewDraft(draft.getDraftId());
        assertNotNull(preview);
        assertNull(preview.getId(), "Preview invoice entity must not be persisted");
        draft = draftService.getDraft(draft.getDraftId()).orElseThrow();
        assertEquals("PREVIEW", draft.getDraftState());
        int previewVersion = draft.getVersion();

        // 5. Execute draft in unified transaction
        Invoice executed = draftService.executeDraft(draft.getDraftId(), previewVersion, "TEST-INST");
        assertNotNull(executed);
        assertNotNull(executed.getId(), "Executed invoice must be persisted");
        assertNotNull(executed.getInvoiceNumber(), "Invoice number must be allocated");

        draft = draftService.getDraft(draft.getDraftId()).orElseThrow();
        assertEquals("EXECUTED", draft.getDraftState());
        assertEquals(executed.getId(), draft.getCreatedDocumentId());

        // Verify delivery outbox record was created in same transaction
        assertEquals(1, deliveryRepo.findAll().size());

        // 6. Idempotency test: Re-executing same draft must return original invoice without duplicating
        Invoice reExecuted = draftService.executeDraft(draft.getDraftId(), previewVersion, "TEST-INST");
        assertEquals(executed.getId(), reExecuted.getId(), "Re-executing draft must return the exact same invoice ID");
    }

    @Test
    void testVersionMismatchRejection() {
        Long chatId = 1003L;
        Long userId = 998877L;

        TelegramDraft draft = draftService.startDraft(chatId, userId, testFirmId, "INVOICE");
        draftService.setCustomer(draft.getDraftId(), testCustomer.getId(), testCustomer.getName(), testCustomer.getPhone());
        draftService.addItem(draft.getDraftId(), testProduct.getId(), testProduct.getName(), null, "Piece", BigDecimal.ONE, BigDecimal.valueOf(100), BigDecimal.ZERO);
        draftService.previewDraft(draft.getDraftId());

        // Attempt execution with stale version
        assertThrows(IllegalStateException.class, () -> {
            draftService.executeDraft(draft.getDraftId(), 999, "TEST-INST");
        });
    }
}
