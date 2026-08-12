package br.com.g9.energiai.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyCostCalculatorTest {

    private final BigDecimal defaultTariff = new BigDecimal("0.75");
    private final EnergyCostCalculator calculator = new EnergyCostCalculator(defaultTariff);

    @Test
    @DisplayName("Deve calcular o custo corretamente para consumo válido")
    void shouldCalculateCostCorrectly() {
        assertEquals(new BigDecimal("315.00"), calculator.calculate(420.0));
    }

    @Test
    @DisplayName("Deve aplicar arredondamento HALF_UP corretamente conforme requisito financeiro")
    void shouldApplyHalfUpRoundingCorrectly() {
        assertEquals(new BigDecimal("7.60"), calculator.calculate(10.127));
        assertEquals(new BigDecimal("7.59"), calculator.calculate(10.126));
    }

    @Test
    @DisplayName("Deve retornar zero quando consumo for nulo")
    void shouldReturnZeroForNullConsumption() {
        assertEquals(new BigDecimal("0.00"), calculator.calculate(null));
    }

    @Test
    @DisplayName("Deve retornar zero para consumo zero")
    void shouldReturnZeroForZeroConsumption() {
        assertEquals(new BigDecimal("0.00"), calculator.calculate(0.0));
    }

    @Test
    @DisplayName("Deve permitir o uso de tarifa customizada via construtor")
    void shouldCalculateCostWithCustomTariff() {
        EnergyCostCalculator customCalculator = new EnergyCostCalculator(new BigDecimal("0.92"));
        assertEquals(new BigDecimal("230.00"), customCalculator.calculate(250.0));
    }

    @Test
    @DisplayName("Deve lançar exceção para consumo negativo")
    void shouldThrowExceptionForNegativeConsumption() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(-1.0));
    }
}
