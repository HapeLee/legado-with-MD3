# Semantic EPUB fixtures

These two small ZIP publications were authored for this repository; they contain no third-party
book content. `epub2.epub` uses OPF 2 + NCX. `epub3.epub` uses OPF 3 + NCX and an EPUB navigation
XHTML. Both contain two chapter fragments in one XHTML, a stylesheet with a hidden paragraph,
a valid generated 1×1 black PNG, entity/emoji text and bidirectional chapter links.

ZIP timestamps are fixed. Inspect or edit their contents with a ZIP tool; keep `mimetype` first and
uncompressed. The tests exercise the repository's EPUB reader and semantic pipeline, not full
EPUB conformance. No external downloads or epubcheck installation are needed to run them.

## Shared compatibility fixture

`compatibility.html` and `compatibility.css` exercise inline emphasis, ordered-list markers,
a separator, table cells, preformatted lines, images/anchors in focused tests and CSS visibility.
`compatibility.expected.json` freezes the old local plain-text paragraphs and one old BGM record.
The former local extraction (`HtmlFormatter.formatKeepImg` plus the single EPUB entity decode,
then normal paragraph trimming) produces those goldens; tests must not derive expected values
from the current presentation parser. Table cells deliberately remain separate old paragraphs.
CSS `display:none` on `.reader-visible` must not remove the canonical final paragraph.
These shared resources are used by projection, measurement and Room-backed consumer tests.

The same fixture includes a body link and consecutive visual breaks without changing the frozen
main-branch text or BGM ordinals. Processing integration tests enable presentation through the
production entry and replace only file loading; layout tests also check link hit testing, list
markers and zero-character blank lines. A passing text/coordinate assertion alone is not evidence
that rich appearance reached pagination.
