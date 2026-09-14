package com.billing.simple.billsoft.assistant.nlu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenNlpIntentClassifierTest {

    private OpenNlpIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new OpenNlpIntentClassifier();
        classifier.init();
    }

    @Test
    void testCustomerOutstandingIntents() {
        var res1 = classifier.classify("show customer balance for Amit");
        assertThat(res1.getTopCategory()).isEqualTo("QH_CUSTOMER_OUTSTANDING");

        var res2 = classifier.classify("Amit ka udhari kitna hai");
        assertThat(res2.getTopCategory()).isEqualTo("QH_CUSTOMER_OUTSTANDING");

        var res3 = classifier.classify("ग्राहकाची उधारी किती आहे");
        assertThat(res3.getTopCategory()).isEqualTo("QH_CUSTOMER_OUTSTANDING");
    }

    @Test
    void testExpenseIntents() {
        var res1 = classifier.classify("total expenses this month");
        assertThat(res1.getTopCategory()).isEqualTo("QH_EXPENSE_SUMMARY");

        var res2 = classifier.classify("aaj ka kharcha kitna hua");
        assertThat(res2.getTopCategory()).isEqualTo("QH_EXPENSE_SUMMARY");
    }

    @Test
    void testStockIntents() {
        var res1 = classifier.classify("check product stock");
        assertThat(res1.getTopCategory()).isEqualTo("QH_STOCK_STATUS");

        var res2 = classifier.classify("stock kitna baki hai");
        assertThat(res2.getTopCategory()).isEqualTo("QH_STOCK_STATUS");
    }

    @Test
    void testActionIntents() {
        var res1 = classifier.classify("create new invoice for Amit");
        assertThat(res1.getTopCategory()).isEqualTo("ACT_CREATE_INVOICE");

        var res2 = classifier.classify("receive payment of 5000");
        assertThat(res2.getTopCategory()).isEqualTo("ACT_RECEIVE_PAYMENT");
    }
}
