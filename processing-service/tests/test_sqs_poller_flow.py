import asyncio
import json

import pytest

from app.services import sqs_poller


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
            "Messages": [
                {
                    "ReceiptHandle": "receipt-1",
                    "Body": self.body_payload,
                }
            ]
        }

    def delete_message(self, **kwargs):
        self.deleted.append(kwargs)


def _run_until_cancelled(mocker):
    mocker.patch("app.services.sqs_poller.asyncio.sleep", side_effect=asyncio.CancelledError())
    with pytest.raises(asyncio.CancelledError):
        asyncio.run(sqs_poller.start_polling())


def test_start_polling_success_path_should_publish_and_delete(mocker):
    body = json.dumps({
        "Message": json.dumps({
            "diagramId": "diag-100",
            "s3Key": "diagrams/diag-100.png",
            "eventType": "FILE_UPLOADED"
        })
    })
    sqs = StubSqs(body)

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)
    mock_download = mocker.patch("app.services.sqs_poller.download_file")
    mock_process = mocker.patch("app.services.sqs_poller.process_image", return_value="OCR RESULT")
    mock_publish = mocker.patch("app.services.sqs_poller.publish_diagram_processed_event")
    mocker.patch("app.services.sqs_poller.os.path.exists", return_value=False)

    _run_until_cancelled(mocker)

    mock_download.assert_called_once()
    download_args = mock_download.call_args.args
    assert download_args[0] == "diagrams/diag-100.png"
    assert download_args[1].endswith("diag-100.bin")

    mock_process.assert_called_once()
    process_args = mock_process.call_args.args
    assert process_args[0].endswith("diag-100.bin")
    mock_publish.assert_called_once_with("diag-100", "OCR RESULT", None)
    assert len(sqs.deleted) == 1


def test_start_polling_invalid_payload_should_publish_failure_and_delete(mocker):
    body = json.dumps({
        "Message": json.dumps({
            "diagramId": "diag-200",
            "eventType": "FILE_UPLOADED"
        })
    })
    sqs = StubSqs(body)

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mock_publish_failed = mocker.patch("app.services.sqs_poller.publish_processing_failed_event")

    _run_until_cancelled(mocker)

    mock_publish_failed.assert_called_once_with("diag-200", "invalid_payload_missing_diagramId_or_s3Key", None)
    assert len(sqs.deleted) == 1


def test_start_polling_processing_exception_should_publish_failure_and_delete(mocker):
    body = json.dumps({
        "Message": json.dumps({
            "diagramId": "diag-poison",
            "s3Key": "diagrams/diag-poison.png",
            "eventType": "FILE_UPLOADED"
        })
    })
    sqs = StubSqs(body)

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mocker.patch("app.services.sqs_poller.should_skip_duplicate", return_value=False)
    mocker.patch("app.services.sqs_poller.download_file")
    
    # Injetando uma Poison Pill: O processamento falha com exceção arbitrária
    mock_process = mocker.patch("app.services.sqs_poller.process_image", side_effect=RuntimeError("Poison pill crash!"))
    mock_publish_failed = mocker.patch("app.services.sqs_poller.publish_processing_failed_event")

    _run_until_cancelled(mocker)

    mock_process.assert_called_once()
    # E o sistema deve notificar o erro em vez de engasgar e quebrar o worker
    mock_publish_failed.assert_called_once()
    # A mensagem TEM que ser deletada (ou ir para DLQ se configurado) para não ficar num loop infinito
    assert len(sqs.deleted) == 1


def test_start_polling_malformed_json_should_delete_and_not_publish_failure(mocker):
    sqs = StubSqs("{invalid-json")

    mocker.patch("app.services.sqs_poller.get_sqs_client", return_value=sqs)
    mock_publish_failed = mocker.patch("app.services.sqs_poller.publish_processing_failed_event")

    _run_until_cancelled(mocker)

    mock_publish_failed.assert_not_called()
    assert len(sqs.deleted) == 1
