package ma.enset.bank.account.repository;

import java.util.UUID;
import ma.enset.bank.account.domain.BalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceOperationRepository extends JpaRepository<BalanceOperation, UUID> {
}
