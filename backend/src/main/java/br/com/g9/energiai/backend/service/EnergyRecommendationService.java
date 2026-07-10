package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import java.util.List;

public interface EnergyRecommendationService {
    List<String> generate(EnergyAnalysisRequest request, EnergyCategory categoria);
}
