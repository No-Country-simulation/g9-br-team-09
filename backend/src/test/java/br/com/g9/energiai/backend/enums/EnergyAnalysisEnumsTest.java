package br.com.g9.energiai.backend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EnergyAnalysisEnumsTest {

    @Test
    void shouldExposeExpectedPropertyTypeValues() {
        assertArrayEquals(
            new PropertyType[] {
                PropertyType.CASA,
                PropertyType.APARTAMENTO,
                PropertyType.COMERCIO,
                PropertyType.ESCRITORIO,
                PropertyType.INDUSTRIA,
                PropertyType.OUTRO
            },
            PropertyType.values()
        );
    }

    @Test
    void shouldExposeExpectedEnergyCategoryValues() {
        assertArrayEquals(
            new EnergyCategory[] {
                EnergyCategory.EFICIENTE,
                EnergyCategory.MODERADO,
                EnergyCategory.INEFICIENTE
            },
            EnergyCategory.values()
        );
    }

    @Test
    void shouldExposeExpectedClassificationSourceValues() {
        assertArrayEquals(
            new ClassificationSource[] {
                ClassificationSource.RULE_BASED,
                ClassificationSource.ML_MODEL,
                ClassificationSource.RULE_BASED_FALLBACK
            },
            ClassificationSource.values()
        );
    }
}
