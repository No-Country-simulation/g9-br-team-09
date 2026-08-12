package br.com.g9.energiai.backend.controller;

import br.com.g9.energiai.backend.documentation.EnergyAnalysisApi;
import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDashboardResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisDetailResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisListResponse;
import br.com.g9.energiai.backend.dto.response.EnergyAnalysisResponse;
import br.com.g9.energiai.backend.service.EnergyAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analise-energetica")
@RequiredArgsConstructor
public class EnergyAnalysisController implements EnergyAnalysisApi {

    private final EnergyAnalysisService energyAnalysisService;

    @Override
    @PostMapping
    public ResponseEntity<EnergyAnalysisResponse> createAnalysis(@RequestBody @Valid EnergyAnalysisRequest request) {
        return ResponseEntity.ok(energyAnalysisService.analyze(request));
    }

    @Override
    @GetMapping
    public ResponseEntity<EnergyAnalysisListResponse> listAnalyses(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(energyAnalysisService.findAll(pageable));
    }

    @Override
    @GetMapping("/resumo")
    public ResponseEntity<EnergyAnalysisDashboardResponse> getDashboardSummary() {
        return ResponseEntity.ok(energyAnalysisService.getDashboardSummary());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<EnergyAnalysisDetailResponse> getAnalysisById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(energyAnalysisService.findById(id));
    }
}
