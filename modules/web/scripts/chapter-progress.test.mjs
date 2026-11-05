import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/chapterProgress.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2021, module: ts.ModuleKind.ESNext },
})
const { paragraphAtPosition } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

test('restoration uses server offsets despite entity, emoji and HTML lengths', () => {
  const paragraphs = [
    { html: '<p>A&amp;<b>😀B</b></p>', start: 0, endExclusive: 7 },
    { html: 'tail', start: 7, endExclusive: 12 },
  ]
  assert.equal(paragraphAtPosition(paragraphs, 2), 0)
  assert.equal(paragraphAtPosition(paragraphs, 7), 1)
  assert.equal(paragraphAtPosition(paragraphs, 11), 1)
  assert.equal(paragraphAtPosition(paragraphs, 12), 1)
  assert.equal(paragraphs[paragraphAtPosition(paragraphs, 10)].start, 7)
  assert.equal(paragraphAtPosition([], 0), -1)
})
