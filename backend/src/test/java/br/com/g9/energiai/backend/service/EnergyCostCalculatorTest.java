package br.com.g9.energiai.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    @DisplayName("Deve calcular o custo corretamente com tarifa customizada")
    void shouldCalculateCostWithCustomTariff() {
        EnergyCostCalculator calculator = new EnergyCostCalculator(new BigDecimal("0.92"));
        assertEquals(new BigDecimal("230.00"), calculator.calculate(250.0));
    }

    @Test
    @DisplayName("Deve retornar zero para consumo zero")
    void shouldReturnZeroForZeroConsumption() {
        EnergyCostCalculator calculator = new EnergyCostCalculator(new BigDecimal("0.75"));
        assertEquals(new BigDecimal("0.00"), calculator.calculate(0.0));
    }

    @Test
    @DisplayName("Deve lancar excecao para consumo negativo")
    void shouldThrowExceptionForNegativeConsumption() {
        EnergyCostCalculator calculator = new EnergyCostCalculator(new BigDecimal("0.75"));

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(-1.0));
    }
}
