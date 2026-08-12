package br.com.g9.energiai.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class EnergyCostCalculator {

    private final BigDecimal tariff;

    public EnergyCostCalculator(@Value("${energy.tariff.default}") BigDecimal tariff) {
        this.tariff = tariff;
    }

    public BigDecimal calculate(Double consumptionKwh) {
        if (consumptionKwh == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (consumptionKwh < 0) {
            throw new IllegalArgumentException("consumptionKwh must be greater than or equal to zero");
        }

        return BigDecimal.valueOf(consumptionKwh)
                .multiply(tariff)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
