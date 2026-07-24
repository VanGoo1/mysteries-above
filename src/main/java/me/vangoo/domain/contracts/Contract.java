package me.vangoo.domain.contracts;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Обопільний контракт (Sun, Seq 6): {@code partyA} пропонує, {@code partyB} засвідчує згодою
 * (ефект-шар збирає її через {@code monitorSneaking}, як {@code Telepathy}). Persisted лише
 * ПІСЛЯ підпису ({@link #sign()}) — незасвідчена пропозиція живе тільки в пам'яті
 * {@code ContractService} й не переживає рестарт.
 */
public class Contract {

    public enum ContractState { PROPOSED, ACTIVE, SETTLED, BREACHED }

    private final UUID id;
    private final UUID partyA;
    private final UUID partyB;
    private final ContractTerm term;
    private final Map<String, String> params;
    private final long createdAtEpochMillis;
    private ContractState state;

    private Contract(UUID id, UUID partyA, UUID partyB, ContractTerm term,
                      Map<String, String> params, long createdAtEpochMillis, ContractState state) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(partyA);
        Objects.requireNonNull(partyB);
        Objects.requireNonNull(term);
        Objects.requireNonNull(state);
        if (partyA.equals(partyB)) {
            throw new IllegalArgumentException("Contract parties must differ");
        }
        validateParams(term, params);
        this.id = id;
        this.partyA = partyA;
        this.partyB = partyB;
        this.term = term;
        this.params = Map.copyOf(params);
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.state = state;
    }

    public static Contract propose(UUID partyA, UUID partyB, ContractTerm term, Map<String, String> params) {
        return new Contract(UUID.randomUUID(), partyA, partyB, term, params,
                System.currentTimeMillis(), ContractState.PROPOSED);
    }

    /** Гідрація з persisted-стану ({@code JSONContractRepository}). */
    public static Contract restore(UUID id, UUID partyA, UUID partyB, ContractTerm term,
                                    Map<String, String> params, long createdAtEpochMillis, ContractState state) {
        return new Contract(id, partyA, partyB, term, params, createdAtEpochMillis, state);
    }

    public void sign() {
        requireState(ContractState.PROPOSED);
        state = ContractState.ACTIVE;
    }

    public void settle() {
        requireState(ContractState.ACTIVE);
        state = ContractState.SETTLED;
    }

    public void breach() {
        requireState(ContractState.ACTIVE);
        state = ContractState.BREACHED;
    }

    private void requireState(ContractState expected) {
        if (state != expected) {
            throw new IllegalStateException("Contract " + id + " must be " + expected + " but is " + state);
        }
    }

    public boolean involves(UUID player) {
        return partyA.equals(player) || partyB.equals(player);
    }

    public UUID other(UUID player) {
        if (partyA.equals(player)) return partyB;
        if (partyB.equals(player)) return partyA;
        throw new IllegalArgumentException("Player " + player + " is not a party of contract " + id);
    }

    public UUID id() { return id; }
    public UUID partyA() { return partyA; }
    public UUID partyB() { return partyB; }
    public ContractTerm term() { return term; }
    public Map<String, String> params() { return params; }
    public long createdAtEpochMillis() { return createdAtEpochMillis; }
    public ContractState state() { return state; }

    private static void validateParams(ContractTerm term, Map<String, String> params) {
        switch (term) {
            case PEACE -> { /* без додаткових параметрів */ }
            case DEBT -> requireKeys(term, params, "amount", "deadlineEpochMillis");
        }
    }

    private static void requireKeys(ContractTerm term, Map<String, String> params, String... keys) {
        for (String key : keys) {
            if (!params.containsKey(key)) {
                throw new IllegalArgumentException("Contract term " + term + " requires param \"" + key + "\"");
            }
        }
    }
}
