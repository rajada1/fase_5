"""
Serviço de análise de arquitetura via IA.

Integração com o pipeline da equipe IADT:
1. Copia o diagrama para o bucket S3 da IADT (tech-challenger-diagrams-prod)
2. O worker-service no EKS detecta o upload e processa com YOLO + Bedrock LLM
3. O resultado é salvo no DynamoDB (tech-challenger-diagrams-prod)
4. Este serviço faz polling no DynamoDB até o resultado estar disponível
5. Converte o resultado para o formato AnalysisResult do nosso sistema
"""

import json
import logging
import time
import uuid

import boto3

from app.core.config import settings
from app.models.analysis import AnalysisResult, Risk

logger = logging.getLogger(__name__)

# Configuração do pipeline IADT
IADT_S3_BUCKET = "tech-challenger-diagrams-prod-639642128870"
IADT_S3_PREFIX = "diagrams/"
IADT_DYNAMODB_TABLE = "tech-challenger-diagrams-prod"
IADT_POLL_INTERVAL = 5  # segundos entre cada check
IADT_POLL_MAX_ATTEMPTS = 24  # máximo 2 minutos de espera (24 * 5s)


class NonRetryableAnalysisError(Exception):
    """Erro permanente na análise. Mensagem não será reprocessada."""
    pass


def _get_s3_client():
    client_args = {"region_name": settings.AWS_REGION}
    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client("s3", **client_args)


def _get_dynamodb_client():
    client_args = {"region_name": settings.AWS_REGION}
    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client("dynamodb", **client_args)


def _copy_to_iadt_bucket(s3_key: str, s3_bucket: str) -> str:
    """Copia o diagrama do bucket SOAT para o bucket IADT e retorna a key no destino."""
    s3 = _get_s3_client()

    # Baixar do bucket SOAT
    response = s3.get_object(Bucket=s3_bucket, Key=s3_key)
    file_bytes = response["Body"].read()

    # Gerar nome único para evitar colisão
    filename = s3_key.split("/")[-1]
    iadt_key = f"{IADT_S3_PREFIX}{filename}"

    # Upload para o bucket IADT
    s3.put_object(
        Bucket=IADT_S3_BUCKET,
        Key=iadt_key,
        Body=file_bytes,
        ContentType=response.get("ContentType", "image/png"),
    )

    logger.info(
        "Diagrama copiado para bucket IADT: s3://%s/%s",
        IADT_S3_BUCKET,
        iadt_key,
    )
    return iadt_key


def _poll_dynamodb_for_result(iadt_s3_key: str) -> dict:
    """Faz polling no DynamoDB até o resultado da análise IADT estar disponível."""
    dynamodb = _get_dynamodb_client()

    for attempt in range(1, IADT_POLL_MAX_ATTEMPTS + 1):
        # Scan filtrando pela s3_key (o IADT usa s3_key como referência)
        response = dynamodb.scan(
            TableName=IADT_DYNAMODB_TABLE,
            FilterExpression="s3_key = :key AND #s = :status",
            ExpressionAttributeNames={"#s": "status"},
            ExpressionAttributeValues={
                ":key": {"S": iadt_s3_key},
                ":status": {"S": "completed"},
            },
        )

        items = response.get("Items", [])
        if items:
            logger.info(
                "Resultado IADT encontrado no DynamoDB após %d tentativas. s3_key=%s",
                attempt,
                iadt_s3_key,
            )
            return items[0]

        logger.info(
            "Aguardando resultado IADT (tentativa %d/%d)...",
            attempt,
            IADT_POLL_MAX_ATTEMPTS,
        )
        time.sleep(IADT_POLL_INTERVAL)

    raise NonRetryableAnalysisError(
        f"Timeout aguardando resultado da análise IADT para {iadt_s3_key}. "
        f"Máximo de {IADT_POLL_MAX_ATTEMPTS * IADT_POLL_INTERVAL}s excedido."
    )


def _parse_iadt_result(dynamo_item: dict) -> AnalysisResult:
    """Converte o resultado do DynamoDB (formato IADT) para AnalysisResult (formato SOAT)."""

    # Extrair componentes detectados pelo YOLO
    elements = dynamo_item.get("elements_detected", {}).get("L", [])
    components = [elem["S"] for elem in elements if "S" in elem]

    # Extrair relatório de análise do LLM
    analysis_report_raw = dynamo_item.get("analysis_report", {}).get("S", "{}")

    try:
        report = json.loads(analysis_report_raw)
    except json.JSONDecodeError:
        report = {}

    # Converter riscos do formato IADT para o formato SOAT
    risks = []
    iadt_risks = report.get("riscos_identificados", [])
    for r in iadt_risks:
        if isinstance(r, dict):
            risks.append(
                Risk(
                    type=r.get("risco", r.get("descricao", "risk_detected")),
                    description=r.get("impacto", r.get("descricao", "")),
                    severity=r.get("severidade", "medium"),
                    mitigation=r.get("recomendacao", r.get("acao", None)),
                )
            )

    # Converter recomendações
    recommendations = []
    iadt_recs = report.get("recomendacoes_melhoria", [])
    for rec in iadt_recs:
        if isinstance(rec, dict):
            desc = rec.get("recomendacao", rec.get("descricao", ""))
            beneficio = rec.get("beneficio", rec.get("acao", ""))
            recommendations.append(f"{desc} — {beneficio}" if beneficio else desc)
        elif isinstance(rec, str):
            recommendations.append(rec)

    # Enriquecer componentes com a análise detalhada do LLM
    iadt_components = report.get("analise_componentes", [])
    for comp in iadt_components:
        if isinstance(comp, dict):
            servico = comp.get("servico", "")
            caso_uso = comp.get("caso_uso", "")
            if servico and servico not in components:
                components.append(servico)
            if caso_uso:
                components.append(f"{servico}: {caso_uso}")

    # Garantir que temos pelo menos algo nos componentes
    if not components:
        components = ["Nenhum componente identificado pelo modelo de IA"]

    if not risks:
        risks = [
            Risk(
                type="no_risks_detected",
                description="O modelo não identificou riscos específicos neste diagrama.",
                severity="low",
                mitigation=None,
            )
        ]

    if not recommendations:
        recommendations = ["Nenhuma recomendação gerada pelo modelo."]

    return AnalysisResult(
        components=components,
        risks=risks,
        recommendations=recommendations,
    )


def analyze_architecture(s3_key: str, s3_bucket: str) -> AnalysisResult:
    """
    Analisa um diagrama de arquitetura integrando com o pipeline IADT.

    Fluxo:
    1. Copia o diagrama para o bucket S3 da IADT
    2. O EKS worker-service da IADT processa (YOLO + Bedrock LLM)
    3. Faz polling no DynamoDB até o resultado estar disponível
    4. Converte e retorna no formato AnalysisResult
    """
    if not s3_key:
        raise NonRetryableAnalysisError("s3_key vazio ou inválido.")

    logger.info(
        "Iniciando análise integrada com pipeline IADT. s3://%s/%s",
        s3_bucket,
        s3_key,
    )

    # 1. Copiar diagrama para o bucket IADT
    try:
        iadt_s3_key = _copy_to_iadt_bucket(s3_key, s3_bucket)
    except Exception as e:
        raise NonRetryableAnalysisError(
            f"Falha ao copiar diagrama para bucket IADT: {e}"
        )

    # 2. Aguardar processamento pelo EKS (polling DynamoDB)
    dynamo_item = _poll_dynamodb_for_result(iadt_s3_key)

    # 3. Converter resultado para formato SOAT
    result = _parse_iadt_result(dynamo_item)

    logger.info(
        "Análise IADT concluída. Componentes=%d, Riscos=%d, Recomendações=%d",
        len(result.components),
        len(result.risks),
        len(result.recommendations),
    )

    return result
