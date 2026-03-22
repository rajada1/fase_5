from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    AWS_REGION: str = "us-east-1"
    AWS_ENDPOINT_URL: str = ""
    AWS_ACCESS_KEY_ID: str = ""
    AWS_SECRET_ACCESS_KEY: str = ""
    S3_BUCKET_NAME: str = "architecture-diagrams-dev"
    SQS_QUEUE_URL: str = "http://localhost:4566/000000000000/file-uploaded-queue-dev"
    SNS_TOPIC_ARN: str = "arn:aws:sns:us-east-1:000000000000:diagram-processed-topic-dev"
    DEDUPE_WINDOW_SECONDS: int = 300
    REDIS_URL: str = ""

    model_config = SettingsConfigDict(env_file=".env")

settings = Settings()
