from prometheus_client import Counter

messages_received_total = Counter(
    "processing_messages_received_total",
    "Total de mensagens SQS recebidas pelo processing-service",
)

messages_processed_total = Counter(
    "processing_messages_processed_total",
    "Total de mensagens processadas com sucesso no processing-service",
)

messages_failed_total = Counter(
    "processing_messages_failed_total",
    "Total de falhas de processamento de mensagens no processing-service",
)

messages_deduped_total = Counter(
    "processing_messages_deduped_total",
    "Total de mensagens ignoradas por deduplicacao no processing-service",
)
