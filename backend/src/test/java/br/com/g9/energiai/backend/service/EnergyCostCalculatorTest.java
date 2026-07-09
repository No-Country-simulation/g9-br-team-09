package br.com.g9.energiai.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyCostCalculatorTest {

    @Test
    @DisplayName("Deve calcular o custo corretamente seguindo a tarifa de 0.75")
    void shouldCalculateCostCorrectly() {
        // Arrange (Configuração)
        BigDecimal tariff = new BigDecimal("0.75");
        EnergyCostCalculator calculator = new EnergyCostCalculator(tariff);
        Double consumption = 420.0;
        BigDecimal expectedResult = new BigDecimal("315.00");

        // Act (Execução)
        BigDecimal result = calculator.calculate(consumption);

        // Assert (Verificação)
        assertEquals(expectedResult, result);
    }

    @Test
    @DisplayName("Deve retornar zero para consumo nulo")
    void shouldReturnZeroForNullConsumption() {
        EnergyCostCalculator calculator = new EnergyCostCalculator(new BigDecimal("0.75"));
        assertEquals(new BigDecimal("0.00"), calculator.calculate(null));
    }
}
