package br.com.g9.energiai.backend.entity;

import br.com.g9.energiai.backend.enums.ClassificationSource;
import br.com.g9.energiai.backend.enums.EnergyCategory;
import br.com.g9.energiai.backend.enums.PropertyType;
import jakarta.persistence.*;
import lombok.*;
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

    @ElementCollection
    @CollectionTable(name = "energy_analysis_recommendations", joinColumns = @JoinColumn(name = "analysis_id"))
    @Column(name = "recommendation")
    private List<String> recomendacoes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
