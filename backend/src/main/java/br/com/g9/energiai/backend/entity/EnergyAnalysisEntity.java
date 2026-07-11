package br.com.g9.energiai.backend.entity;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import br.com.g9.energiai.backend.persistence.converter.RecommendationListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "energy_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumo_kwh", nullable = false)
    private Double consumoKwh;

    @Column(name = "uso_horario_pico", nullable = false)
    private Boolean usoHorarioPico;

    @Column(name = "quantidade_equipamentos", nullable = false)
    private Integer quantidadeEquipamentos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_imovel", nullable = false)
    private PropertyType tipoImovel;

    @Column(name = "horas_alto_consumo", nullable = false)
    private Integer horasAltoConsumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnergyCategory categoria;

    @Column(nullable = false)
    private Double probabilidade;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "custo_estimado_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal custoEstimadoMensal;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_classificacao", nullable = false)
    private ClassificationSource fonteClassificacao;

    @Builder.Default
    @Lob
    @Convert(converter = RecommendationListConverter.class)
    @Column(name = "recomendacoes", nullable = false)
    private List<String> recomendacoes = List.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
