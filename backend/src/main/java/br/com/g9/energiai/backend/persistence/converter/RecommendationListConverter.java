package br.com.g9.energiai.backend.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class RecommendationListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_OF_STRING_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> recommendations) {
        List<String> safeRecommendations = recommendations == null ? List.of() : List.copyOf(recommendations);

        try {
            return OBJECT_MAPPER.writeValueAsString(safeRecommendations);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Não foi possível serializar as recomendações", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            return List.copyOf(OBJECT_MAPPER.readValue(value, LIST_OF_STRING_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Não foi possível desserializar as recomendações", exception);
        }
    }
}
