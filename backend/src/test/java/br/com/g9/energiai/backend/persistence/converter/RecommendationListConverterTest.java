package br.com.g9.energiai.backend.persistence.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationListConverterTest {

    private final RecommendationListConverter converter = new RecommendationListConverter();

    @Test
    @DisplayName("Deve serializar a lista de recomendações para JSON")
    void shouldSerializeRecommendationsToJson() {
        String json = converter.convertToDatabaseColumn(List.of("Recomendação 1", "Recomendação 2"));

        assertEquals("[\"Recomendação 1\",\"Recomendação 2\"]", json);
    }

    @Test
    @DisplayName("Deve desserializar o JSON para lista de recomendações")
    void shouldDeserializeJsonToRecommendations() {
        List<String> recommendations = converter.convertToEntityAttribute("[\"Recomendação 1\",\"Recomendação 2\"]");

        assertIterableEquals(List.of("Recomendação 1", "Recomendação 2"), recommendations);
    }

    @Test
    @DisplayName("Deve converter lista vazia para JSON válido")
    void shouldSerializeEmptyListToValidJson() {
        String json = converter.convertToDatabaseColumn(List.of());

        assertEquals("[]", json);
    }

    @Test
    @DisplayName("Deve tratar valor nulo ou vazio como lista vazia")
    void shouldReturnEmptyListForNullOrBlankValue() {
        assertIterableEquals(List.of(), converter.convertToEntityAttribute(null));
        assertIterableEquals(List.of(), converter.convertToEntityAttribute(""));
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("Deve lançar exceção clara quando o JSON das recomendações for inválido")
    void shouldThrowClearExceptionForInvalidJson() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> converter.convertToEntityAttribute("[recomendacao-invalida")
        );

        assertTrue(exception.getMessage().contains("desserializar as recomendações"));
    }
}
