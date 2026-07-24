package me.vangoo.application.services.context;

import me.vangoo.application.services.ContractService;
import me.vangoo.domain.abilities.context.IContractContext;
import me.vangoo.domain.contracts.Contract;
import me.vangoo.domain.contracts.ContractTerm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ContractContext implements IContractContext {

    private final ContractService contractService;

    public ContractContext(ContractService contractService) {
        this.contractService = contractService;
    }

    @Override
    public Contract propose(UUID partyA, UUID partyB, ContractTerm term, Map<String, String> params) {
        return contractService.propose(partyA, partyB, term, params);
    }

    @Override
    public void cancelProposal(UUID contractId) {
        contractService.cancelProposal(contractId);
    }

    @Override
    public Optional<Contract> sign(UUID contractId) {
        return contractService.sign(contractId);
    }

    @Override
    public Optional<Contract> settle(UUID contractId) {
        return contractService.settle(contractId);
    }

    @Override
    public Optional<Contract> findActiveBetween(UUID a, UUID b) {
        return contractService.findActiveBetween(a, b);
    }
}
