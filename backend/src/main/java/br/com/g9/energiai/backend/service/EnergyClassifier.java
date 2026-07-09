package br.com.g9.energiai.backend.service;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;

public interface EnergyClassifier {
    EnergyAnalysisResponse classify(EnergyAnalysisRequest request);
}
