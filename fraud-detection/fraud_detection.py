"""Partie H : détection de fraude par classification binaire.

Usage : python fraud_detection.py [--data ../bank_transactions.csv] [--output results]
"""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

SEED = 42
TARGET = "is_fraud"
IDENTIFIERS = ["transaction_id", "account_id"]
CATEGORICAL = ["country", "merchant_category"]
NUMERIC = ["amount", "hour", "transactions_last_24h", "average_amount_last_7d"]


def build_pipeline() -> Pipeline:
    numeric = Pipeline([("impute", SimpleImputer(strategy="median")), ("scale", StandardScaler())])
    categorical = Pipeline([
        ("impute", SimpleImputer(strategy="most_frequent")),
        ("encode", OneHotEncoder(handle_unknown="ignore")),
    ])
    preprocessing = ColumnTransformer([("num", numeric, NUMERIC), ("cat", categorical, CATEGORICAL)])
    model = LogisticRegression(class_weight="balanced", max_iter=1000, random_state=SEED)
    return Pipeline([("preprocessing", preprocessing), ("model", model)])


def best_f1_threshold(y_true, scores) -> float:
    precision, recall, thresholds = precision_recall_curve(y_true, scores)
    f1 = 2 * precision[:-1] * recall[:-1] / np.maximum(precision[:-1] + recall[:-1], 1e-12)
    return float(thresholds[int(np.argmax(f1))])


def evaluate(y_true, scores, threshold: float) -> dict:
    predicted = (scores >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, predicted, labels=[0, 1]).ravel()
    return {
        "threshold": round(threshold, 4),
        "accuracy": round(accuracy_score(y_true, predicted), 4),
        "precision_fraud": round(precision_score(y_true, predicted, zero_division=0), 4),
        "recall_fraud": round(recall_score(y_true, predicted, zero_division=0), 4),
        "f1_fraud": round(f1_score(y_true, predicted, zero_division=0), 4),
        "average_precision": round(average_precision_score(y_true, scores), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=Path(__file__).parent.parent / "bank_transactions.csv")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "results")
    args = parser.parse_args()

    # 1. Chargement
    df = pd.read_csv(args.data)
    print(f"Lignes : {len(df)}, colonnes : {df.shape[1]}")

    # 2. Proportion de fraudes et valeurs manquantes
    fraud_rate = df[TARGET].mean()
    missing = df.isna().sum()
    print(f"Proportion de fraudes : {fraud_rate:.2%} ({int(df[TARGET].sum())} fraudes)")
    print("Valeurs manquantes par colonne :")
    print(missing.to_string())
    print(f"Doublons de transaction_id : {df['transaction_id'].duplicated().sum()}")

    # 3 et 4. Séparation stratifiée, identifiants exclus des variables explicatives
    X = df.drop(columns=[TARGET, *IDENTIFIERS])
    y = df[TARGET]
    X_fit, X_test, y_fit, y_test = train_test_split(X, y, test_size=0.2, stratify=y, random_state=SEED)
    # Le seuil se choisit sur une validation issue de l'entraînement, jamais sur le test
    X_train, X_val, y_train, y_val = train_test_split(
        X_fit, y_fit, test_size=0.25, stratify=y_fit, random_state=SEED
    )
    print(f"Entraînement : {len(X_train)}, validation : {len(X_val)}, test : {len(X_test)}")

    # 5 et 6. Pipeline ajusté sur l'entraînement uniquement, puis modèle simple
    pipeline = build_pipeline().fit(X_train, y_train)

    threshold = best_f1_threshold(y_val, pipeline.predict_proba(X_val)[:, 1])
    print(f"Seuil retenu sur la validation : {threshold:.4f}")

    # 7. Évaluation sur le test, avec le seuil par défaut et le seuil retenu
    test_scores = pipeline.predict_proba(X_test)[:, 1]
    results = {
        "dataset": {"rows": len(df), "fraud_rate": round(float(fraud_rate), 4),
                    "missing_values": {k: int(v) for k, v in missing.items() if v}},
        "baseline_always_legit_accuracy": round(1 - float(y_test.mean()), 4),
        "test_threshold_0_5": evaluate(y_test, test_scores, 0.5),
        "test_threshold_validation": evaluate(y_test, test_scores, threshold),
    }
    print(json.dumps(results, indent=2))

    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "metrics.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
