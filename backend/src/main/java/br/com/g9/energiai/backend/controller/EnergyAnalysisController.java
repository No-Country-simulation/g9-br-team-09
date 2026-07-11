package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.service.EnergyAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analise-energetica")
@RequiredArgsConstructor
public class EnergyAnalysisController {

    private final EnergyAnalysisService energyAnalysisService;

    @PostMapping
    public ResponseEntity<EnergyAnalysisResponse> createAnalysis(@RequestBody @Valid EnergyAnalysisRequest request) {
        EnergyAnalysisResponse response = energyAnalysisService.analyze(request);
        return ResponseEntity.ok(response);
    }
}
