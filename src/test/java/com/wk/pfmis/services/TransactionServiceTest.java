package com.wk.pfmis.services;

import com.wk.pfmis.domain.Money;
import com.wk.pfmis.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionServiceTest {

    @Test
    void recordValidatesAndConvertsMoneyAtRepositoryBoundary() {
        RecordingRepository repository = new RecordingRepository();
        TransactionService service = new TransactionService(repository);

        service.record(command(Money.parseMajor("123.45", "MWK")));

        assertEquals(123.45, repository.recorded.legacyAmount(), 0.001);
        assertEquals("INCOME", repository.recorded.transactionType());
        assertEquals("NORMAL", repository.recorded.purpose());
    }

    @Test
    void rejectsNonPositiveAmountsBeforeRepositoryWrite() {
        RecordingRepository repository = new RecordingRepository();
        TransactionService service = new TransactionService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.record(command(Money.zero("MWK"))));
        assertEquals(0, repository.recordCalls);
    }

    private TransactionService.TransactionCommand command(Money amount) {
        return new TransactionService.TransactionCommand(
                1,
                null,
                null,
                null,
                null,
                " INCOME ",
                " NORMAL ",
                " COMPLETED ",
                amount,
                LocalDate.of(2026, 8, 17),
                " Test income ",
                null,
                null
        );
    }

    private static final class RecordingRepository extends TransactionRepository {
        private TransactionWriteRequest recorded;
        private int recordCalls;

        @Override
        public void record(TransactionWriteRequest request) {
            this.recorded = request;
            this.recordCalls++;
        }
    }
}
