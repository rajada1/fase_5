import json
import logging
from google import genai
from google.genai import types
from app.core.config import settings
from app.models.analysis import AnalysisResult

logger = logging.getLogger(__name__)


class NonRetryableAnalysisError(Exception):
    pass


def _sanitize_extracted_data(extracted_data: str) -> str:
    if extracted_data is None:
        return ""

    without_null = extracted_data.replace("\x00", " ")
    without_control_chars = "".join(
        ch for ch in without_null if ch in "\n\r\t" or ord(ch) >= 32
    )

    neutralized_delimiters = (
        without_control_chars
        .replace("<", "‹")
        .replace(">", "›")
    )

    sanitized = neutralized_delimiters.strip()

    if len(sanitized) > settings.LLM_INPUT_MAX_CHARS:
        logger.warning(
            "Texto extraído muito longo (%s caracteres). Truncando para %s caracteres.",
            len(sanitized),
            settings.LLM_INPUT_MAX_CHARS,
        )
        sanitized = sanitized[: settings.LLM_INPUT_MAX_CHARS]

    return sanitized


def _extract_json_content(raw_response: str) -> str:
    response = (raw_response or "").strip()
    if response.startswith("```"):
        lines = response.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip().startswith("```"):
            lines = lines[:-1]
        response = "\n".join(lines).strip()
    return response


def _is_non_retryable_quota_error(message: str) -> bool:
    normalized = (message or "").lower()
    return any(
        token in normalized
        for token in (
            "insufficient",
            "quota",
            "billing",
            "payment",
            "exceeded your current quota",
        )
    )


def _is_retryable_provider_error(message: str) -> bool:
    normalized = (message or "").lower()
    return any(
        token in normalized
        for token in (
            "rate limit",
            "too many requests",
            "resource exhausted",
            "unavailable",
            "timeout",
            "timed out",
            "deadline",
            "internal",
            "503",
            "500",
        )
    )

def analyze_architecture(extracted_data: str) -> AnalysisResult:
    safe_extracted_data = _sanitize_extracted_data(extracted_data)
    if not safe_extracted_data:
        raise NonRetryableAnalysisError("Texto extraído vazio ou inválido para análise.")

    client = genai.Client(api_key=settings.GEMINI_API_KEY)

    system_instruction = (
        "Você é um Arquiteto de Software Cloud especialista. "
        "Responda APENAS com um JSON válido seguindo exatamente este schema: "
        '{"components":["string"],"risks":[{"type":"string","description":"string"}],"recommendations":["string"]}'
    )

    user_prompt = f"Analise o texto extraído abaixo:\n{safe_extracted_data}\n"

    try:
        response = client.models.generate_content(
            model=settings.GEMINI_MODEL,
            contents=user_prompt,
            config=types.GenerateContentConfig(
                system_instruction=system_instruction,
                response_mime_type="application/json",
                temperature=0.2,
            ),
        )
    except Exception as provider_error:
        message = str(provider_error)
        logger.error("Falha no provedor LLM: %s", message)

        if _is_non_retryable_quota_error(message):
            raise NonRetryableAnalysisError(
                f"Falha não-retryable na análise de arquitetura: {message}"
            ) from provider_error

        if _is_retryable_provider_error(message):
            raise

        raise NonRetryableAnalysisError(
            f"Falha não-retryable na análise de arquitetura: {message}"
        ) from provider_error

    result_content = _extract_json_content(getattr(response, "text", ""))
    logger.info("Resposta bruta da Gemini: %s", result_content)

    try:
        data = json.loads(result_content)
        return AnalysisResult(**data)
    except Exception as parse_error:
        raise NonRetryableAnalysisError(
            f"Falha não-retryable na análise de arquitetura: resposta inválida do modelo ({parse_error})"
        ) from parse_error
