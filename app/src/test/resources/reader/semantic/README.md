# Semantic EPUB fixtures

These two small ZIP publications were authored for this repository; they contain no third-party
book content. `epub2.epub` uses OPF 2 + NCX. `epub3.epub` uses OPF 3 + NCX and an EPUB navigation
XHTML. Both contain two chapter fragments in one XHTML, a stylesheet with a hidden paragraph,
a valid generated 1×1 black PNG, entity/emoji text and bidirectional chapter links.

ZIP timestamps are fixed. Inspect or edit their contents with a ZIP tool; keep `mimetype` first and
uncompressed. The tests exercise the repository's EPUB reader and semantic pipeline, not full
EPUB conformance. No external downloads or epubcheck installation are needed to run them.
