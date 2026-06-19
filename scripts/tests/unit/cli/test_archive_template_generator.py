"""Unit tests for ArchiveTemplateGenerator — pure logic, no real I/O."""

import random

from cli.generate_archive_template import FILE_TYPES, ArchiveTemplateGenerator


class TestMagicBytes:
    def test_pdf_starts_with_pdf_header(self):
        assert ArchiveTemplateGenerator.pdf_bytes().startswith(b"%PDF-1.4")

    def test_jpg_starts_with_jfif_marker(self):
        data = ArchiveTemplateGenerator.jpg_bytes()
        assert data[:4] == bytes([0xFF, 0xD8, 0xFF, 0xE0])

    def test_jpg_ends_with_eoi_marker(self):
        data = ArchiveTemplateGenerator.jpg_bytes()
        assert data[-2:] == bytes([0xFF, 0xD9])

    def test_png_starts_with_png_signature(self):
        data = ArchiveTemplateGenerator.png_bytes()
        assert data[:8] == bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])

    def test_gif_starts_with_gif89a(self):
        assert ArchiveTemplateGenerator.gif_bytes()[:6] == b"GIF89a"

    def test_webp_starts_with_riff(self):
        data = ArchiveTemplateGenerator.webp_bytes()
        assert data[:4] == b"RIFF"
        assert data[8:12] == b"WEBP"

    def test_mp4_starts_with_ftyp_box(self):
        data = ArchiveTemplateGenerator.mp4_bytes()
        assert data[4:8] == b"ftyp"

    def test_webm_starts_with_ebml_id(self):
        data = ArchiveTemplateGenerator.webm_bytes()
        assert data[:4] == bytes([0x1A, 0x45, 0xDF, 0xA3])


class TestTextContent:
    def test_txt_content_has_lines(self):
        rng = random.Random(42)
        content = ArchiveTemplateGenerator.make_txt_content(rng)
        assert "Line" in content

    def test_txt_content_is_deterministic_with_seed(self):
        c1 = ArchiveTemplateGenerator.make_txt_content(random.Random(42))
        c2 = ArchiveTemplateGenerator.make_txt_content(random.Random(42))
        assert c1 == c2

    def test_csv_content_has_header(self):
        rng = random.Random(42)
        content = ArchiveTemplateGenerator.make_csv_content(rng)
        assert content.startswith("Name,Value,Date\n")

    def test_csv_content_is_deterministic_with_seed(self):
        c1 = ArchiveTemplateGenerator.make_csv_content(random.Random(42))
        c2 = ArchiveTemplateGenerator.make_csv_content(random.Random(42))
        assert c1 == c2


class TestRandomExtension:
    def test_returns_extension_from_file_types(self):
        rng = random.Random(0)
        ext = ArchiveTemplateGenerator.random_extension(rng)
        valid = {e for e, _ in FILE_TYPES}
        assert ext in valid

    def test_deterministic_with_seed(self):
        e1 = ArchiveTemplateGenerator.random_extension(random.Random(7))
        e2 = ArchiveTemplateGenerator.random_extension(random.Random(7))
        assert e1 == e2

    def test_distribution_matches_weights(self):
        rng = random.Random(0)
        counts: dict[str, int] = {}
        total = 10_000
        for _ in range(total):
            ext = ArchiveTemplateGenerator.random_extension(rng)
            counts[ext] = counts.get(ext, 0) + 1
        # .txt has weight 25/100 = 25%, allow ±5%
        txt_pct = counts.get(".txt", 0) / total * 100
        assert 20 <= txt_pct <= 30

    def test_custom_single_type_always_returns_that_type(self):
        rng = random.Random(123)
        types = [(".zip", 1)]
        for _ in range(20):
            assert ArchiveTemplateGenerator.random_extension(rng, types) == ".zip"


class TestWriteFile:
    def test_writes_binary_for_pdf(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.pdf"
        gen.write_file(p, ".pdf")
        assert p.read_bytes().startswith(b"%PDF")

    def test_writes_binary_for_jpg(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.jpg"
        gen.write_file(p, ".jpg")
        data = p.read_bytes()
        assert data[:2] == bytes([0xFF, 0xD8])

    def test_writes_text_for_txt(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.txt"
        gen.write_file(p, ".txt")
        assert "Line" in p.read_text(encoding="utf-8")

    def test_writes_csv_header_for_csv(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.csv"
        gen.write_file(p, ".csv")
        assert p.read_text(encoding="utf-8").startswith("Name,Value,Date")


class TestWriteFileEdgeCases:
    def test_writes_binary_for_jpeg_alias(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.jpeg"
        gen.write_file(p, ".jpeg")
        assert p.read_bytes()[:2] == bytes([0xFF, 0xD8])

    def test_writes_text_for_unknown_extension(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        p = tmp_path / "test.xyz"
        gen.write_file(p, ".xyz")
        assert "Line" in p.read_text(encoding="utf-8")


class TestShims:
    def test_get_random_extension_returns_valid_ext(self):
        from cli.generate_archive_template import get_random_extension

        assert get_random_extension() in {e for e, _ in FILE_TYPES}

    def test_create_valid_file_shim_creates_file(self, tmp_path):
        from cli.generate_archive_template import create_valid_file

        p = tmp_path / "out.txt"
        create_valid_file(p, ".txt")
        assert p.exists()

    def test_create_numbered_files_shim_creates_correct_count(self, tmp_path):
        from cli.generate_archive_template import create_numbered_files

        create_numbered_files(tmp_path / "d", 4, "pfx_")
        assert len(list((tmp_path / "d").iterdir())) == 4


class TestMain:
    def test_generates_output_dir(self, tmp_path, monkeypatch):
        import cli.generate_archive_template as mod

        monkeypatch.setattr(mod, "OUTPUT_DIR", tmp_path / "out")
        mod.main()
        assert (tmp_path / "out").exists()


class TestCreateFiles:
    def test_creates_correct_count(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(42))
        gen.create_files(tmp_path / "out", 5)
        created = list((tmp_path / "out").iterdir())
        assert len(created) == 5

    def test_creates_directory_if_missing(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(1))
        target = tmp_path / "nested" / "dir"
        assert not target.exists()
        gen.create_files(target, 3)
        assert target.exists()

    def test_prefix_applied_to_filenames(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(0))
        gen.create_files(tmp_path / "p", 4, prefix="pfx_")
        names = [f.name for f in (tmp_path / "p").iterdir()]
        assert all(n.startswith("pfx_") for n in names)

    def test_zero_padding_based_on_count(self, tmp_path):
        gen = ArchiveTemplateGenerator(random.Random(0))
        gen.create_files(tmp_path / "z", 100)
        names = sorted(f.stem for f in (tmp_path / "z").iterdir())
        # zero-padded to 3 digits (count=100 → width=3)
        assert all(len(n) >= 3 for n in names)
