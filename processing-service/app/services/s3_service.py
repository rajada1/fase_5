import boto3
from app.core.config import settings


def get_s3_client():
    client_args = {"region_name": settings.AWS_REGION}
    if settings.AWS_ENDPOINT_URL:
        client_args["endpoint_url"] = settings.AWS_ENDPOINT_URL
    if settings.AWS_ACCESS_KEY_ID and settings.AWS_SECRET_ACCESS_KEY:
        client_args["aws_access_key_id"] = settings.AWS_ACCESS_KEY_ID
        client_args["aws_secret_access_key"] = settings.AWS_SECRET_ACCESS_KEY
    return boto3.client('s3', **client_args)


def check_file_exists(s3_key: str) -> bool:
    """Verifica se o arquivo existe no S3."""
    s3 = get_s3_client()
    try:
        s3.head_object(Bucket=settings.S3_BUCKET_NAME, Key=s3_key)
        return True
    except s3.exceptions.ClientError:
        return False


def download_file(s3_key: str, local_path: str) -> str:
    """Baixa arquivo do S3 para path local."""
    s3 = get_s3_client()
    s3.download_file(settings.S3_BUCKET_NAME, s3_key, local_path)
    return local_path
