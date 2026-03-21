from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    AWS_REGION: str = "us-east-1"
    AWS_ENDPOINT_URL: str = "http://localhost:4566"
    SQS_QUEUE_URL: str = "http://localhost:4566/000000000000/diagram-processed-queue-dev"
    SNS_TOPIC_ARN: str = "arn:aws:sns:us-east-1:000000000000:analysis-completed-topic-dev"
    OPENAI_API_KEY: str = "mock-key"

    model_config = SettingsConfigDict(env_file=".env")

settings = Settings()
