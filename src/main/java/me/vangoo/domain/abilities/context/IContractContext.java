package me.vangoo.domain.abilities.context;

import me.vangoo.domain.contracts.Contract;
import me.vangoo.domain.contracts.ContractTerm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IContractContext {
    Contract propose(UUID partyA, UUID partyB, ContractTerm term, Map<String, String> params);

    void cancelProposal(UUID contractId);

    Optional<Contract> sign(UUID contractId);

    Optional<Contract> settle(UUID contractId);

    Optional<Contract> findActiveBetween(UUID a, UUID b);
}
