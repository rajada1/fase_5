"""
Serviço de análise de arquitetura via IA.

=== CONTRATO DE INTEGRAÇÃO (para equipe IADT) ===

INPUT:
    s3_key (str):    Chave do arquivo no S3 (ex: "diagrams/uuid.png")
    s3_bucket (str): Nome do bucket S3

    O arquivo pode ser uma imagem (PNG, JPG) ou PDF de um diagrama de arquitetura.
    A equipe IADT decide como processar: OCR, visão computacional, LLM multimodal, etc.

OUTPUT:
    AnalysisResult com:
        - components: List[str]       → Componentes identificados no diagrama
        - risks: List[Risk]           → Riscos arquiteturais detectados
            - Risk.type: str          → Tipo do risco (ex: "single_point_of_failure")
            - Risk.description: str   → Descrição do risco
            - Risk.severity: str|None → Severidade (high/medium/low)
            - Risk.mitigation: str|None → Sugestão de mitigação
        - recommendations: List[str]  → Recomendações de melhoria

ERROS:
    - NonRetryableAnalysisError: Erro permanente (arquivo inválido, quota, etc.)
      → Mensagem é descartada da fila e evento de falha é publicado.
    - Qualquer outra Exception: Erro transitório (timeout, rate limit, etc.)
      → Mensagem volta para a fila e será reprocessada (max 3 tentativas antes de DLQ).

COMO BAIXAR O ARQUIVO DO S3:
    import boto3
    s3 = boto3.client('s3', region_name='us-east-1')
    response = s3.get_object(Bucket=s3_bucket, Key=s3_key)
    file_bytes = response['Body'].read()
"""

import logging
from app.core.config import settings
from app.models.analysis import AnalysisResult, Risk

logger = logging.getLogger(__name__)


class NonRetryableAnalysisError(Exception):
    """Erro permanente na análise. Mensagem não será reprocessada."""
    pass


def analyze_architecture(s3_key: str, s3_bucket: str) -> AnalysisResult:
    """
    Analisa um diagrama de arquitetura armazenado no S3.

    TODO: Substituir este stub pela implementação real da equipe IADT.
    A implementação deve:
    1. Baixar o arquivo do S3 (s3_key, s3_bucket)
    2. Processar o arquivo (OCR, visão computacional, LLM, etc.)
    3. Retornar um AnalysisResult com componentes, riscos e recomendações
    """
    if not s3_key:
        raise NonRetryableAnalysisError("s3_key vazio ou inválido.")

    logger.info(
        "Análise solicitada para arquivo s3://%s/%s. "
        "STUB ativo — aguardando implementação da equipe IADT.",
        s3_bucket, s3_key,
    )

    # =========================================================
    # STUB: Resposta placeholder até a equipe IADT implementar
    # =========================================================
    return AnalysisResult(
        components=["[STUB] Componentes serão identificados pela IA"],
        risks=[
            Risk(
                type="stub_response",
                description="Análise real ainda não implementada. Este é um placeholder.",
                severity="low",
                mitigation="Aguardar integração com serviço de IA da equipe IADT.",
            )
        ],
        recommendations=[
            "[STUB] Recomendações serão geradas pela IA após integração com equipe IADT."
        ],
    )
