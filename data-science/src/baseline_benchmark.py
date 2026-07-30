"""Benchmark diagnóstico da baseline determinística.

Este módulo não altera o gerador, não produz o dataset oficial e não
serializa modelos. Ele utiliza apenas as cinco features de produção.
"""

import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, make_scorer
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.tree import DecisionTreeClassifier

import schema


def prepare_benchmark_data(
    sample: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.Series]:
    """Separa as features permitidas e o target oficial."""
    features = sample.loc[
        :,
        list(schema.FEATURE_COLUMNS),
    ].copy()

    target = sample.loc[
        :,
        schema.TARGET_COLUMN,
    ].copy()

    return features, target


def build_preprocessor(
    feature_columns: tuple[str, ...],
) -> ColumnTransformer:
    """Cria o pré-processamento para um subconjunto de features."""
    if not feature_columns:
        raise ValueError("feature_columns não pode estar vazio")

    invalid_features = sorted(
        set(feature_columns).difference(schema.FEATURE_COLUMNS)
    )

    if invalid_features:
        raise ValueError(
            "Features inválidas: " + ", ".join(invalid_features)
        )

    categorical_features = [
        feature
        for feature in feature_columns
        if feature == "tipo_imovel"
    ]
    numerical_features = [
        feature
        for feature in feature_columns
        if feature != "tipo_imovel"
    ]

    transformers = []

    if numerical_features:
        transformers.append(
            (
                "numerical",
                StandardScaler(),
                numerical_features,
            )
        )

    if categorical_features:
        transformers.append(
            (
                "categorical",
                OneHotEncoder(
                    handle_unknown="ignore",
                    sparse_output=False,
                ),
                categorical_features,
            )
        )

    return ColumnTransformer(transformers=transformers)


def split_benchmark_data(
    features: pd.DataFrame,
    target: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> tuple[
    pd.DataFrame,
    pd.DataFrame,
    pd.DataFrame,
    pd.Series,
    pd.Series,
    pd.Series,
]:
    """Divide os dados em treino, validação e teste estratificados."""
    (
        x_train,
        x_remaining,
        y_train,
        y_remaining,
    ) = train_test_split(
        features,
        target,
        test_size=0.30,
        random_state=seed,
        stratify=target,
    )

    (
        x_validation,
        x_test,
        y_validation,
        y_test,
    ) = train_test_split(
        x_remaining,
        y_remaining,
        test_size=0.50,
        random_state=seed,
        stratify=y_remaining,
    )

    return (
        x_train,
        x_validation,
        x_test,
        y_train,
        y_validation,
        y_test,
    )


def evaluate_dummy_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia o piso de comparação com a classe mais frequente."""
    model = DummyClassifier(
        strategy="most_frequent",
        random_state=seed,
    )
    model.fit(x_train, y_train)

    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def evaluate_logistic_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia uma Regressão Logística com pré-processamento."""
    preprocessor = build_preprocessor(schema.FEATURE_COLUMNS)

    model = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "classifier",
                LogisticRegression(
                    max_iter=2_000,
                    random_state=seed,
                ),
            ),
        ]
    )

    model.fit(x_train, y_train)
    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def evaluate_tree_baseline(
    x_train: pd.DataFrame,
    y_train: pd.Series,
    x_validation: pd.DataFrame,
    y_validation: pd.Series,
    seed: int = schema.RANDOM_SEED,
) -> float:
    """Avalia uma Árvore de Decisão simples com pré-processamento."""
    preprocessor = build_preprocessor(schema.FEATURE_COLUMNS)

    model = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "classifier",
                DecisionTreeClassifier(
                    max_depth=5,
                    random_state=seed,
                ),
            ),
        ]
    )

    model.fit(x_train, y_train)
    predictions = model.predict(x_validation)

    return float(
        f1_score(
            y_validation,
            predictions,
            labels=list(schema.ENERGY_CATEGORIES),
            average="macro",
            zero_division=0,
        )
    )


def run_baseline_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Executa os modelos diagnósticos na mesma divisão estratificada."""
    features, target = prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = split_benchmark_data(
        features,
        target,
        seed=seed,
    )

    return {
        "dummy": evaluate_dummy_baseline(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=seed,
        ),
        "regressao_logistica": evaluate_logistic_baseline(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=seed,
        ),
        "arvore_decisao": evaluate_tree_baseline(
            x_train,
            y_train,
            x_validation,
            y_validation,
            seed=seed,
        ),
    }


def run_single_feature_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia a Regressão Logística com uma feature por vez."""
    features, target = prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = split_benchmark_data(
        features,
        target,
        seed=seed,
    )

    results: dict[str, float] = {}

    for feature in schema.FEATURE_COLUMNS:
        selected_columns = [feature]

        model = Pipeline(
            steps=[
                (
                    "preprocessor",
                    build_preprocessor((feature,)),
                ),
                (
                    "classifier",
                    LogisticRegression(
                        max_iter=2_000,
                        random_state=seed,
                    ),
                ),
            ]
        )

        model.fit(
            x_train.loc[:, selected_columns],
            y_train,
        )
        predictions = model.predict(
            x_validation.loc[:, selected_columns]
        )

        results[feature] = float(
            f1_score(
                y_validation,
                predictions,
                labels=list(schema.ENERGY_CATEGORIES),
                average="macro",
                zero_division=0,
            )
        )

    return results


def build_leave_one_feature_out_feature_sets() -> dict[str, tuple[str, ...]]:
    """Cria os subconjuntos usados na ablação de uma feature por vez."""
    return {
        removed_feature: tuple(
            feature
            for feature in schema.FEATURE_COLUMNS
            if feature != removed_feature
        )
        for removed_feature in schema.FEATURE_COLUMNS
    }


def run_leave_one_feature_out_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
) -> dict[str, float]:
    """Avalia a Regressão Logística removendo uma feature por execução."""
    features, target = prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = split_benchmark_data(
        features,
        target,
        seed=seed,
    )

    feature_sets = build_leave_one_feature_out_feature_sets()
    results: dict[str, float] = {}

    for removed_feature, selected_features in feature_sets.items():
        selected_columns = list(selected_features)

        model = Pipeline(
            steps=[
                (
                    "preprocessor",
                    build_preprocessor(selected_features),
                ),
                (
                    "classifier",
                    LogisticRegression(
                        max_iter=2_000,
                        random_state=seed,
                    ),
                ),
            ]
        )

        model.fit(
            x_train.loc[:, selected_columns],
            y_train,
        )
        predictions = model.predict(
            x_validation.loc[:, selected_columns]
        )

        results[removed_feature] = float(
            f1_score(
                y_validation,
                predictions,
                labels=list(schema.ENERGY_CATEGORIES),
                average="macro",
                zero_division=0,
            )
        )

    return results


def run_permutation_importance_logistic_benchmark(
    sample: pd.DataFrame,
    seed: int = schema.RANDOM_SEED,
    n_repeats: int = 10,
) -> dict[str, dict[str, float]]:
    """Calcula a importância por permutação das features originais."""
    if (
        isinstance(n_repeats, bool)
        or not isinstance(n_repeats, int)
        or n_repeats <= 0
    ):
        raise ValueError(
            "n_repeats deve ser um inteiro maior que zero"
        )

    features, target = prepare_benchmark_data(sample)

    (
        x_train,
        x_validation,
        _,
        y_train,
        y_validation,
        _,
    ) = split_benchmark_data(
        features,
        target,
        seed=seed,
    )

    model = Pipeline(
        steps=[
            (
                "preprocessor",
                build_preprocessor(schema.FEATURE_COLUMNS),
            ),
            (
                "classifier",
                LogisticRegression(
                    max_iter=2_000,
                    random_state=seed,
                ),
            ),
        ]
    )

    model.fit(x_train, y_train)

    f1_macro_scorer = make_scorer(
        f1_score,
        labels=list(schema.ENERGY_CATEGORIES),
        average="macro",
        zero_division=0,
    )

    permutation_results = permutation_importance(
        model,
        x_validation,
        y_validation,
        scoring=f1_macro_scorer,
        n_repeats=n_repeats,
        random_state=seed,
        n_jobs=1,
    )

    return {
        feature: {
            "importance_mean": float(importance_mean),
            "importance_std": float(importance_std),
        }
        for feature, importance_mean, importance_std in zip(
            schema.FEATURE_COLUMNS,
            permutation_results.importances_mean,
            permutation_results.importances_std,
            strict=True,
        )
    }
