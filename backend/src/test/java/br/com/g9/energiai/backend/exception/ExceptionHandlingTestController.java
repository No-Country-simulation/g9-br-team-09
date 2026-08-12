package br.com.g9.energiai.backend.exception;

import br.com.g9.energiai.backend.dto.request.EnergyAnalysisRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExceptionHandlingTestController {

    @PostMapping(path = "/test/analise-energetica", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    EnergyAnalysisRequest analyze(@Valid @RequestBody EnergyAnalysisRequest request) {
        return request;
    }

    @GetMapping("/test/resource-not-found")
    String resourceNotFound() {
        throw new ResourceNotFoundException("Análise não encontrada");
    }

    @GetMapping("/test/required-param")
    String requiredParam(@RequestParam Integer pagina) {
        return "pagina=" + pagina;
    }

    @GetMapping("/test/path-param/{id}")
    String pathParam(@PathVariable Integer id) {
        return "id=" + id;
    }

    @GetMapping("/test/unexpected-error")
    String unexpectedError() {
        throw new IllegalStateException("segredo-interno-42");
    }
}
