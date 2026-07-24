package me.vangoo.application.services;

import me.vangoo.domain.contracts.Contract;
import me.vangoo.domain.contracts.ContractTerm;
import me.vangoo.infrastructure.contracts.JSONContractRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Реєстр контрактів Sun (Seq 6). Непідписані ({@code PROPOSED}) пропозиції живуть лише в
 * пам'яті — на диск ідуть тільки {@code ACTIVE}/{@code SETTLED}/{@code BREACHED} записи
 * (мірор {@code SecretOrderService}: application-шар, Bukkit дозволено напряму).
 */
public class ContractService {

    private static final Logger LOGGER = Logger.getLogger(ContractService.class.getName());

    private final JSONContractRepository repository;
    private final DivinePunishment divinePunishment;
    private final Map<UUID, Contract> contracts = new ConcurrentHashMap<>();

    public ContractService(JSONContractRepository repository, DivinePunishment divinePunishment) {
        this.repository = repository;
        this.divinePunishment = divinePunishment;
        load();
    }

    public Contract propose(UUID partyA, UUID partyB, ContractTerm term, Map<String, String> params) {
        Contract contract = Contract.propose(partyA, partyB, term, params);
        contracts.put(contract.id(), contract);
        return contract;
    }

    public void cancelProposal(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract != null && contract.state() == Contract.ContractState.PROPOSED) {
            contracts.remove(contractId);
        }
    }

    public synchronized Optional<Contract> sign(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null || contract.state() != Contract.ContractState.PROPOSED) {
            return Optional.empty();
        }
        contract.sign();
        persist();
        return Optional.of(contract);
    }

    public synchronized Optional<Contract> settle(UUID contractId) {
        Contract contract = contracts.get(contractId);
        if (contract == null || contract.state() != Contract.ContractState.ACTIVE) {
            return Optional.empty();
        }
        contract.settle();
        persist();
        return Optional.of(contract);
    }

    /** Порушує контракт і карає {@code breachingParty} Божественною карою (holy lightning +
     * true damage + печатка здібностей 10-15 хв + Зламаний Сонячний Диск). Прогрес Beyonder'а
     * кара не чіпає — це не смерть із втратою шляху. */
    public synchronized Optional<Contract> breach(UUID contractId, UUID breachingParty) {
        Contract contract = contracts.get(contractId);
        if (contract == null || contract.state() != Contract.ContractState.ACTIVE) {
            return Optional.empty();
        }
        contract.breach();
        persist();
        divinePunishment.punish(breachingParty);
        return Optional.of(contract);
    }

    public List<Contract> getActiveContractsFor(UUID player) {
        return contracts.values().stream()
                .filter(c -> c.state() == Contract.ContractState.ACTIVE && c.involves(player))
                .toList();
    }

    public Optional<Contract> findActiveBetween(UUID a, UUID b) {
        return getActiveContractsFor(a).stream()
                .filter(c -> c.involves(b))
                .findFirst();
    }

    public Optional<Contract> findActivePeace(UUID attacker, UUID victim) {
        return getActiveContractsFor(attacker).stream()
                .filter(c -> c.term() == ContractTerm.PEACE && c.involves(victim))
                .findFirst();
    }

    /** Активна Клятва, де {@code debtor} (partyB) винен {@code creditor} (partyA). Напрям важливий. */
    public Optional<Contract> findActiveDebt(UUID debtor, UUID creditor) {
        return getActiveContractsFor(debtor).stream()
                .filter(c -> c.term() == ContractTerm.DEBT
                        && c.partyB().equals(debtor) && c.partyA().equals(creditor))
                .findFirst();
    }

    public List<Contract> getActiveDebts() {
        return contracts.values().stream()
                .filter(c -> c.state() == Contract.ContractState.ACTIVE && c.term() == ContractTerm.DEBT)
                .toList();
    }

    private void persist() {
        Map<String, JSONContractRepository.ContractRecord> records = new HashMap<>();
        for (Contract contract : contracts.values()) {
            if (contract.state() == Contract.ContractState.PROPOSED) continue;
            records.put(contract.id().toString(), toRecord(contract));
        }
        repository.save(new JSONContractRepository.Model(records));
    }

    private void load() {
        repository.load().ifPresent(model -> {
            if (model.contracts() == null) return;
            for (JSONContractRepository.ContractRecord record : model.contracts().values()) {
                try {
                    Contract contract = Contract.restore(
                            UUID.fromString(record.id()),
                            UUID.fromString(record.partyA()),
                            UUID.fromString(record.partyB()),
                            ContractTerm.valueOf(record.term()),
                            record.params(),
                            record.createdAtEpochMillis(),
                            Contract.ContractState.valueOf(record.state())
                    );
                    contracts.put(contract.id(), contract);
                } catch (IllegalArgumentException | NullPointerException e) {
                    LOGGER.warning("Skipping corrupt contract record: " + e.getMessage());
                }
            }
        });
    }

    private JSONContractRepository.ContractRecord toRecord(Contract contract) {
        return new JSONContractRepository.ContractRecord(
                contract.id().toString(),
                contract.partyA().toString(),
                contract.partyB().toString(),
                contract.term().name(),
                contract.params(),
                contract.createdAtEpochMillis(),
                contract.state().name()
        );
    }
}
