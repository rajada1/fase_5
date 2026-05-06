import asyncio
import json

import pytest

from app.services import sqs_poller
from app.services.llm_service import NonRetryableAnalysisError


class StubSqs:
    def __init__(self, body_payload: str):
        self.body_payload = body_payload
        self.deleted = []
        self._delivered = False

    def receive_message(self, **kwargs):
        if self._delivered:
            return {}
        self._delivered = True
        return {
            "Messages": [{
                "ReceiptHandle": "receipt-1",
                "Body": self.body_payload,
            }]
        }

    def delete_message(self, **kwargs):
        self.deleted.append(kwargs)


def _run_until_cancelled(mocker):
    mocker.patch("app.services.sqs_poller.asyncio.sleep", side_effect=asyncio.CancelledError())
    with pytest.raises(asyncio.CancelledError):
        asyncio.run(sqs_poller.start_polling())


def test_start_polling_success_path_should_publish_completed_and_delete(mocker):
    body = json.dumps({
        "Message": json.dumps({
            "diagramId": "diag-300",
            "s3Key": "diagrams/diag-300.png",
            "s3Bucket": "my-bucket",
            "eventType": "DIAGRAM_PROCESSED"
        })
    })
    sqs = StubSqs(body)

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)
    mock_result = mocker.Mock()
    mock_analyze = mocker.patch("app.services.sqs_poller.analyze_architecture", return_value=mock_result)
    mock_publish_ok = mocker.patch("app.services.sqs_poller.publish_analysis_completed_event")

    _run_until_cancelled(mocker)

    mock_analyze.assert_called_once_with("diagrams/diag-300.png", "my-bucket")
    mock_publish_ok.assert_called_once_with("diag-300", mock_result, None)
    assert len(sqs.deleted) == 1


def test_start_polling_non_retryable_error_should_publish_failed_and_delete(mocker):
    body = json.dumps({
        "Message": json.dumps({
            "diagramId": "diag-400",
            "s3Key": "diagrams/diag-400.png",
            "s3Bucket": "my-bucket",
            "eventType": "DIAGRAM_PROCESSED"
        })
    })
    sqs = StubSqs(body)

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)
    mocker.patch(
        "app.services.sqs_poller.analyze_architecture",
        side_effect=NonRetryableAnalysisError("invalid_input")
    )
    mock_publish_failed = mocker.patch("app.services.sqs_poller.publish_analysis_failed_event")

    _run_until_cancelled(mocker)

    mock_publish_failed.assert_called_once_with("diag-400", "invalid_input", None)
    assert len(sqs.deleted) == 1


def test_start_polling_malformed_json_should_delete_and_not_publish_failure(mocker):
    sqs = StubSqs("{invalid-json")

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mock_publish_failed = mocker.patch("app.services.sqs_poller.publish_analysis_failed_event")

    _run_until_cancelled(mocker)

    mock_publish_failed.assert_not_called()
    assert len(sqs.deleted) == 1
