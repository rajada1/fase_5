"""
Serviço de análise de arquitetura via LLM.

=== CONTRATO DE INTEGRAÇÃO (para equipe IADT) ===

INPUT:
    extracted_data (str): Texto extraído do diagrama de arquitetura (OCR).
                          Já sanitizado, máximo de LLM_INPUT_MAX_CHARS caracteres.

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
    - NonRetryableAnalysisError: Erro permanente (payload inválido, quota, etc.)
      → Mensagem é descartada da fila e evento de falha é publicado.
    - Qualquer outra Exception: Erro transitório (timeout, rate limit, etc.)
      → Mensagem volta para a fila e será reprocessada (max 3 tentativas antes de DLQ).

EXEMPLO DE RESPOSTA ESPERADA:
    AnalysisResult(
        components=["API Gateway", "Load Balancer", "Database PostgreSQL"],
        risks=[
            Risk(type="single_point_of_failure", description="Banco sem réplica", severity="high"),
        ],
        recommendations=["Adicionar réplica de leitura ao banco de dados"],
    )
"""

import logging
from app.core.config import settings
from app.models.analysis import AnalysisResult, Risk

logger = logging.getLogger(__name__)


class NonRetryableAnalysisError(Exception):
    """Erro permanente na análise. Mensagem não será reprocessada."""
    pass


def analyze_architecture(extracted_data: str) -> AnalysisResult:
    """
    Analisa texto extraído de um diagrama de arquitetura e retorna
    componentes, riscos e recomendações.

    TODO: Substituir este stub pela implementação real da equipe IADT.
    A implementação pode usar qualquer LLM/modelo (Gemini, OpenAI, Bedrock, etc.)
    desde que retorne um AnalysisResult válido.
    """
    if not extracted_data or not extracted_data.strip():
        raise NonRetryableAnalysisError("Texto extraído vazio ou inválido para análise.")

    # Truncar input se necessário
    safe_data = extracted_data.strip()
    if len(safe_data) > settings.LLM_INPUT_MAX_CHARS:
        logger.warning(
            "Texto extraído muito longo (%d chars). Truncando para %d.",
            len(safe_data),
            settings.LLM_INPUT_MAX_CHARS,
        )
        safe_data = safe_data[: settings.LLM_INPUT_MAX_CHARS]

    logger.info(
        "Análise solicitada para texto com %d caracteres. "
        "STUB ativo — aguardando implementação da equipe IADT.",
        len(safe_data),
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
