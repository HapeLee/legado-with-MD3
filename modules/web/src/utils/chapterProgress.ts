/** Offsets are supplied by the server in the Android reader's UTF-16 body space. */
export const paragraphAtPosition = (
  paragraphs: ReadonlyArray<{ start: number; endExclusive: number }>,
  position: number,
): number => {
  if (paragraphs.length === 0) return -1
  const index = paragraphs.findIndex(paragraph => paragraph.endExclusive > position)
  return index < 0 ? paragraphs.length - 1 : index
}
