from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    AWS_REGION: str = "us-east-1"
    AWS_ENDPOINT_URL: str = ""
    AWS_ACCESS_KEY_ID: str = ""
    AWS_SECRET_ACCESS_KEY: str = ""
    SQS_QUEUE_URL: str = Field(
        default="http://localhost:4566/000000000000/diagram-processed-queue-dev",
        validation_alias=AliasChoices("AI_ANALYSIS_SQS_QUEUE_URL", "SQS_QUEUE_URL"),
    )
    SNS_TOPIC_ARN: str = Field(
        default="arn:aws:sns:us-east-1:000000000000:analysis-completed-topic-dev",
        validation_alias=AliasChoices("AI_ANALYSIS_SNS_TOPIC_ARN", "SNS_TOPIC_ARN"),
    )
    LLM_INPUT_MAX_CHARS: int = 12000
    DEDUPE_WINDOW_SECONDS: int = 300
    DEDUPE_KEY_PREFIX: str = "ai-analysis"
    REDIS_URL: str = ""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
