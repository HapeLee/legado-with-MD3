package io.legado.app.data.repository.manga.translation

internal const val MANGA_AI_COORDINATE_PROMPT = """
Coordinate contract: one attached image has one fixed reference frame. Origin is the TOP LEFT of the entire attached image, including all context margins. X grows rightwards, Y downwards. Left/right = 1000*x/IMAGE_WIDTH; top/bottom = 1000*y/IMAGE_HEIGHT. X and Y use DIFFERENT denominators on non-square images. Corners are (0,0), (1000,0), (1000,1000), (0,1000); center is (500,500).
Never output pixels, 0..1 fractions, percentages, a 1024 scale, page-relative coordinates for a crop, or coordinates relative to a balloon/reading-order window. Do not recenter boxes, swap axes or describe where translated text should go. Use all visible source ink, not a font baseline. Include bottom descenders, last characters, punctuation and detached radicals; sourceLines exclude ruby in body thickness, while textBox covers associated annotations as well.
Echo the supplied frame object exactly. Coordinates must describe observed ink at its actual position. The client will measure fonts and typeset. Check first/last ink extents before returning compact JSON; do not output a layout discussion. Schema example numbers are illustrative, never template locations.
"""
