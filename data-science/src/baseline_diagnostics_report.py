"""Geração reproduzível do relatório de diagnósticos da baseline."""

import argparse
from pathlib import Path
from typing import Sequence

import baseline_benchmark
import dataset
import schema


REQUIRED_RESULT_DESCRIPTION = (
    "O resultado mede a capacidade do modelo de reproduzir "
    "padrões da base sintética sob as condições testadas."
)

MODEL_LABELS = {
    "dummy": "Dummy",
    "regressao_logistica": "Regressão Logística",
    "arvore_decisao": "Árvore de Decisão",
}


def _positive_integer(value: str) -> int:
    """Converte um argumento textual em inteiro estritamente positivo."""
    parsed_value = int(value)

    if parsed_value <= 0:
        raise argparse.ArgumentTypeError(
            "o valor deve ser um inteiro maior que zero"
        )

    return parsed_value


def _dataset_size_argument(value: str) -> int:
    """Aceita somente o tamanho contratual do Dataset EnergIAI V2."""
    parsed_value = _positive_integer(value)

    if parsed_value != schema.DATASET_SIZE:
        raise argparse.ArgumentTypeError(
            "o tamanho da amostra deve ser exatamente "
            f"{schema.DATASET_SIZE}"
        )

    return parsed_value


def _validate_dataset_size(sample_size: int) -> None:
    """Valida o tamanho contratual antes de gerar o relatório."""
    if isinstance(sample_size, bool) or not isinstance(sample_size, int):
        raise TypeError("sample_size deve ser um inteiro")

    if sample_size != schema.DATASET_SIZE:
        raise ValueError(
            "sample_size deve ser exatamente "
            f"{schema.DATASET_SIZE}"
        )


def parse_arguments(
    arguments: Sequence[str] | None = None,
) -> argparse.Namespace:
    """Lê os parâmetros necessários para gerar o relatório."""
    parser = argparse.ArgumentParser(
        description=(
            "Executa os diagnósticos da baseline sintética "
            "e gera um relatório Markdown."
        )
    )
    parser.add_argument(
        "--sample-size",
        type=_dataset_size_argument,
        default=schema.DATASET_SIZE,
        help=(
            "Quantidade contratual de registros sintéticos "
            f"({schema.DATASET_SIZE})."
        ),
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=schema.RANDOM_SEED,
        help="Seed usada na geração e nos modelos.",
    )
    parser.add_argument(
        "--n-repeats",
        type=_positive_integer,
        default=10,
        help="Quantidade de repetições da importância por permutação.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Caminho do relatório Markdown gerado.",
    )

    return parser.parse_args(arguments)


def _format_decimal(value: float) -> str:
    """Formata um número com seis casas e separador decimal brasileiro."""
    return f"{value:.6f}".replace(".", ",")


def _format_percentage(value: float) -> str:
    """Formata uma razão como percentual com uma casa decimal."""
    return f"{value:.1%}".replace(".", ",")


def _render_benchmark_section(
    benchmark_results: dict[str, float],
) -> list[str]:
    """Renderiza a tabela dos modelos diagnósticos básicos."""
    lines = [
        "## Benchmark diagnóstico",
        "",
        "| Modelo | F1-macro |",
        "| --- | ---: |",
    ]

    for model_name in (
        "dummy",
        "regressao_logistica",
        "arvore_decisao",
    ):
        lines.append(
            "| "
            f"{MODEL_LABELS[model_name]} | "
            f"{_format_decimal(benchmark_results[model_name])} |"
        )

    lines.extend(
        [
            "",
            REQUIRED_RESULT_DESCRIPTION,
            "",
        ]
    )

    return lines


def _render_single_feature_section(
    results: dict[str, float],
) -> list[str]:
    """Renderiza o diagnóstico com uma feature por execução."""
    lines = [
        "## Diagnóstico por feature individual",
        "",
        "| Feature | F1-macro |",
        "| --- | ---: |",
    ]

    for feature in schema.FEATURE_COLUMNS:
        lines.append(
            f"| `{feature}` | {_format_decimal(results[feature])} |"
        )

    lines.extend(
        [
            "",
            REQUIRED_RESULT_DESCRIPTION,
            "",
        ]
    )

    return lines


def _render_ablation_section(
    complete_score: float,
    results: dict[str, float],
) -> list[str]:
    """Renderiza o diagnóstico por remoção de uma feature."""
    lines = [
        "## Diagnóstico por ablação",
        "",
        "| Feature removida | F1-macro | Queda absoluta |",
        "| --- | ---: | ---: |",
    ]

    for feature in schema.FEATURE_COLUMNS:
        reduced_score = results[feature]
        absolute_drop = complete_score - reduced_score

        lines.append(
            f"| `{feature}` | "
            f"{_format_decimal(reduced_score)} | "
            f"{_format_decimal(absolute_drop)} |"
        )

    lines.extend(
        [
            "",
            REQUIRED_RESULT_DESCRIPTION,
            "",
        ]
    )

    return lines


def _render_permutation_section(
    results: dict[str, dict[str, float]],
) -> list[str]:
    """Renderiza média e desvio da importância por permutação."""
    lines = [
        "## Permutation importance",
        "",
        "| Feature | Importância média | Desvio-padrão |",
        "| --- | ---: | ---: |",
    ]

    for feature in schema.FEATURE_COLUMNS:
        metrics = results[feature]

        lines.append(
            f"| `{feature}` | "
            f"{_format_decimal(metrics['importance_mean'])} | "
            f"{_format_decimal(metrics['importance_std'])} |"
        )

    lines.extend(
        [
            "",
            REQUIRED_RESULT_DESCRIPTION,
            "",
        ]
    )

    return lines


def _render_guardrail_section(
    complete_score: float,
    single_feature_results: dict[str, float],
) -> list[str]:
    """Consolida o critério inicial de dependência das features."""
    best_feature = max(
        schema.FEATURE_COLUMNS,
        key=single_feature_results.__getitem__,
    )
    best_individual_score = single_feature_results[best_feature]
    ratio = (
        best_individual_score / complete_score
        if complete_score > 0.0
        else float("inf")
    )
    limit = 0.95
    status = "ATENDIDO" if ratio <= limit else "NÃO ATENDIDO"

    return [
        "## Consolidação do guardrail",
        "",
        "| Critério | Resultado |",
        "| --- | ---: |",
        f"| Modelo completo | {_format_decimal(complete_score)} |",
        (
            f"| Melhor feature individual | `{best_feature}` — "
            f"{_format_decimal(best_individual_score)} |"
        ),
        (
            "| Relação entre o melhor resultado individual e o completo | "
            f"{_format_percentage(ratio)} |"
        ),
        f"| Limite inicial | {_format_percentage(limit)} |",
        f"| Situação | **{status}** |",
        "",
        (
            "O guardrail avalia somente a dependência individual das features "
            "na baseline sintética atual."
        ),
        "",
        REQUIRED_RESULT_DESCRIPTION,
        "",
    ]


def render_report(
    sample_size: int,
    seed: int,
    n_repeats: int,
    output_path: Path,
) -> str:
    """Executa os diagnósticos e devolve o relatório completo."""
    _validate_dataset_size(sample_size)

    sample = dataset.generate_audited_sample_with_rare_cases(
        sample_size,
        seed=seed,
    )

    benchmark_results = baseline_benchmark.run_baseline_benchmark(
        sample,
        seed=seed,
    )
    single_feature_results = (
        baseline_benchmark.run_single_feature_logistic_benchmark(
            sample,
            seed=seed,
        )
    )
    ablation_results = (
        baseline_benchmark.run_leave_one_feature_out_logistic_benchmark(
            sample,
            seed=seed,
        )
    )
    permutation_results = (
        baseline_benchmark.run_permutation_importance_logistic_benchmark(
            sample,
            seed=seed,
            n_repeats=n_repeats,
        )
    )

    complete_score = benchmark_results["regressao_logistica"]

    lines = [
        "# Relatório de diagnósticos da baseline — Dataset EnergiAI V2",
        "",
        "## Escopo",
        "",
        (
            "Este relatório registra diagnósticos reproduzíveis executados "
            "sobre a baseline sintética atual."
        ),
        "",
        (
            "Os resultados não constituem evidência causal, validade externa "
            "ou desempenho em dados reais."
        ),
        "",
        "## Configuração reproduzível",
        "",
        "| Parâmetro | Valor |",
        "| --- | ---: |",
        f"| Tamanho da amostra | `{sample_size}` |",
        f"| Seed | `{seed}` |",
        f"| Repetições da permutação | `{n_repeats}` |",
        "",
        "Comando:",
        "",
        "```powershell",
        (
            "python data-science/src/baseline_diagnostics_report.py "
            f"--sample-size {sample_size} "
            f"--seed {seed} "
            f"--n-repeats {n_repeats} "
            f'--output "{output_path.as_posix()}"'
        ),
        "```",
        "",
    ]

    lines.extend(_render_benchmark_section(benchmark_results))
    lines.extend(
        _render_single_feature_section(single_feature_results)
    )
    lines.extend(
        _render_ablation_section(
            complete_score,
            ablation_results,
        )
    )
    lines.extend(_render_permutation_section(permutation_results))
    lines.extend(
        _render_guardrail_section(
            complete_score,
            single_feature_results,
        )
    )

    lines.extend(
        [
            "## Limitações",
            "",
            "- O dataset avaliado é sintético.",
            "- O target da baseline atual é determinístico.",
            "- As métricas refletem as condições específicas desta execução.",
            "- Nenhum resultado deve ser apresentado como desempenho real.",
            "",
        ]
    )

    return "\n".join(lines)


def main(arguments: Sequence[str] | None = None) -> int:
    """Executa o comando e persiste o relatório Markdown."""
    parsed_arguments = parse_arguments(arguments)
    output_path = parsed_arguments.output

    report = render_report(
        sample_size=parsed_arguments.sample_size,
        seed=parsed_arguments.seed,
        n_repeats=parsed_arguments.n_repeats,
        output_path=output_path,
    )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_text(
        report,
        encoding="utf-8",
    )

    print(f"Relatório gerado em: {output_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
