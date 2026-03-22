from prometheus_client import Counter

messages_received_total = Counter(
    "ai_analysis_messages_received_total",
    "Total de mensagens SQS recebidas pelo ai-analysis-service",
)

messages_processed_total = Counter(
    "ai_analysis_messages_processed_total",
    "Total de mensagens processadas com sucesso no ai-analysis-service",
)

messages_failed_total = Counter(
    "ai_analysis_messages_failed_total",
    "Total de falhas de processamento de mensagens no ai-analysis-service",
)

messages_deduped_total = Counter(
    "ai_analysis_messages_deduped_total",
    "Total de mensagens ignoradas por deduplicacao no ai-analysis-service",
)
