"""Unit tests for dataclass models."""

import pytest

from models import BucketType, ExtractionDetails, ExtractionOutput


class TestBucketType:
    def test_safe_value(self):
        assert BucketType.SAFE.name == "SAFE"

    def test_temp_value(self):
        assert BucketType.TEMP.name == "TEMP"

    def test_members(self):
        assert set(BucketType.__members__.keys()) == {"SAFE", "TEMP"}

    def test_lookup_by_name(self):
        assert BucketType["SAFE"] == BucketType.SAFE
        assert BucketType["TEMP"] == BucketType.TEMP

    def test_lookup_unknown_raises(self):
        with pytest.raises(KeyError):
            BucketType["UNKNOWN"]


class TestExtractionDetails:
    def test_creation(self):
        d = ExtractionDetails(doc_id="doc-1", page_number=3, key="title", image_index=0)
        assert d.doc_id == "doc-1"
        assert d.page_number == 3
        assert d.key == "title"
        assert d.image_index == 0

    def test_equality(self):
        a = ExtractionDetails("d", 1, "k", 0)
        b = ExtractionDetails("d", 1, "k", 0)
        assert a == b

    def test_inequality(self):
        a = ExtractionDetails("d", 1, "k", 0)
        b = ExtractionDetails("d", 1, "k", 1)
        assert a != b


class TestExtractionOutput:
    def test_creation(self):
        img = ExtractionDetails("d", 1, "img.png", 1)
        txt = ExtractionDetails("d", 1, "heading", 0)
        out = ExtractionOutput(image_keys=[img], text_keys=[txt], bucket=BucketType.TEMP)
        assert len(out.image_keys) == 1
        assert len(out.text_keys) == 1
        assert out.bucket == BucketType.TEMP

    def test_empty_keys(self):
        out = ExtractionOutput(image_keys=[], text_keys=[], bucket=BucketType.SAFE)
        assert out.image_keys == []
        assert out.text_keys == []

    def test_multiple_keys(self):
        imgs = [
            ExtractionDetails("d", 1, "a.png", 1),
            ExtractionDetails("d", 2, "b.png", 2),
        ]
        txts = [
            ExtractionDetails("d", 1, "t1", 0),
            ExtractionDetails("d", 2, "t2", 0),
            ExtractionDetails("d", 3, "t3", 0),
        ]
        out = ExtractionOutput(image_keys=imgs, text_keys=txts, bucket=BucketType.SAFE)
        assert len(out.image_keys) == 2
        assert len(out.text_keys) == 3
