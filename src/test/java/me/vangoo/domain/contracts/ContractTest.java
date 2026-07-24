package me.vangoo.domain.contracts;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractTest {

    private final UUID partyA = UUID.randomUUID();
    private final UUID partyB = UUID.randomUUID();

    @Test
    void peaceRequiresNoParams() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        assertEquals(Contract.ContractState.PROPOSED, contract.state());
    }

    @Test
    void debtRequiresAmountAndDeadline() {
        assertThrows(IllegalArgumentException.class, () ->
                Contract.propose(partyA, partyB, ContractTerm.DEBT, Map.of("amount", "100")));

        Contract contract = Contract.propose(partyA, partyB, ContractTerm.DEBT,
                Map.of("amount", "100", "deadlineEpochMillis", "999999999999"));
        assertEquals(ContractTerm.DEBT, contract.term());
    }

    @Test
    void partiesMustDiffer() {
        assertThrows(IllegalArgumentException.class, () ->
                Contract.propose(partyA, partyA, ContractTerm.PEACE, Map.of()));
    }

    @Test
    void signMovesProposedToActive() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        contract.sign();
        assertEquals(Contract.ContractState.ACTIVE, contract.state());
    }

    @Test
    void cannotSignTwice() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        contract.sign();
        assertThrows(IllegalStateException.class, contract::sign);
    }

    @Test
    void cannotSettleOrBreachBeforeSigning() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        assertThrows(IllegalStateException.class, contract::settle);
        assertThrows(IllegalStateException.class, contract::breach);
    }

    @Test
    void breachIsTerminal() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        contract.sign();
        contract.breach();
        assertEquals(Contract.ContractState.BREACHED, contract.state());
        assertThrows(IllegalStateException.class, contract::settle);
        assertThrows(IllegalStateException.class, contract::breach);
    }

    @Test
    void settleIsTerminal() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        contract.sign();
        contract.settle();
        assertEquals(Contract.ContractState.SETTLED, contract.state());
        assertThrows(IllegalStateException.class, contract::breach);
    }

    @Test
    void involvesAndOther() {
        Contract contract = Contract.propose(partyA, partyB, ContractTerm.PEACE, Map.of());
        assertTrue(contract.involves(partyA));
        assertTrue(contract.involves(partyB));
        assertFalse(contract.involves(UUID.randomUUID()));
        assertEquals(partyB, contract.other(partyA));
        assertEquals(partyA, contract.other(partyB));
    }

    @Test
    void restoreRehydratesWithoutRegeneratingId() {
        UUID id = UUID.randomUUID();
        Contract restored = Contract.restore(id, partyA, partyB, ContractTerm.DEBT,
                Map.of("amount", "50", "deadlineEpochMillis", "123"), 111L, Contract.ContractState.ACTIVE);
        assertEquals(id, restored.id());
        assertEquals(Contract.ContractState.ACTIVE, restored.state());
        assertEquals(111L, restored.createdAtEpochMillis());
    }
}
