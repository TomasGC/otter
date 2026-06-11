"""Integration-real tests for ArchiveTemplateGenerator — real FS, no mocks."""

import random

from cli.generate_archive_template import ArchiveTemplateGenerator


class TestGenerateStructure:
    def test_generate_creates_expected_folders(self, tmp_path):
        structure = [("small", 3), ("medium", 5)]
        gen = ArchiveTemplateGenerator(random.Random(0))
        gen.generate(tmp_path, structure=structure, root_count=2)

        assert (tmp_path / "small").is_dir()
        assert (tmp_path / "medium").is_dir()

    def test_generate_creates_correct_file_counts_in_folders(self, tmp_path):
        structure = [("folderA", 10), ("folderB", 20)]
        gen = ArchiveTemplateGenerator(random.Random(1))
        gen.generate(tmp_path, structure=structure, root_count=5)

        assert len(list((tmp_path / "folderA").iterdir())) == 10
        assert len(list((tmp_path / "folderB").iterdir())) == 20

    def test_generate_creates_root_files(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(2))
        gen.generate(tmp_path, structure=[], root_count=7)
        root_files = [f for f in tmp_path.iterdir() if f.is_file()]
        assert len(root_files) == 7

    def test_generate_returns_output_dir(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(0))
        result = gen.generate(tmp_path, structure=[], root_count=1)
        assert result == tmp_path

    def test_generated_files_have_valid_extensions(self, tmp_path):
        valid_exts = {e for e, _ in ArchiveTemplateGenerator.FILE_TYPES}
        gen = ArchiveTemplateGenerator(random.Random(3))
        gen.generate(tmp_path, structure=[("x", 50)], root_count=0)
        for f in (tmp_path / "x").iterdir():
            assert f.suffix in valid_exts, f"Unexpected extension: {f.suffix}"

    def test_generated_pdf_has_correct_magic_bytes(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(42))
        gen.generate(tmp_path, structure=[("docs", 50)], root_count=0)
        pdfs = [f for f in (tmp_path / "docs").iterdir() if f.suffix == ".pdf"]
        if pdfs:
            assert pdfs[0].read_bytes().startswith(b"%PDF")

    def test_generated_jpg_has_correct_magic_bytes(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(42))
        gen.generate(tmp_path, structure=[("imgs", 50)], root_count=0)
        jpgs = [f for f in (tmp_path / "imgs").iterdir() if f.suffix == ".jpg"]
        if jpgs:
            data = jpgs[0].read_bytes()
            assert data[:2] == bytes([0xFF, 0xD8])

    def test_generates_idempotently_with_same_seed(self, tmp_path):
        out1 = tmp_path / "run1"
        out2 = tmp_path / "run2"
        structure = [("f", 20)]

        gen1 = ArchiveTemplateGenerator(random.Random(99))
        gen1.generate(out1, structure=structure, root_count=0)

        gen2 = ArchiveTemplateGenerator(random.Random(99))
        gen2.generate(out2, structure=structure, root_count=0)

        names1 = sorted(f.name for f in (out1 / "f").iterdir())
        names2 = sorted(f.name for f in (out2 / "f").iterdir())
        assert names1 == names2
