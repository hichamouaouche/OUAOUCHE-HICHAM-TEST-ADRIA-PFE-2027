package ma.enset.bank.transaction.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Le plafond vient du config-service ; /actuator/refresh le recharge sans redémarrage. */
@ConfigurationProperties("bank.transaction")
public class BankProperties {

    private BigDecimal maxAmount;

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }
}
