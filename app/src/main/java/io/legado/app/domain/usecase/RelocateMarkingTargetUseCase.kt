package io.legado.app.domain.usecase

import io.legado.app.domain.model.TextProcessAnchor

/** Resolves a marking against content from the currently selected source. */
class RelocateMarkingTargetUseCase {

    data class Candidate(val chapterIndex: Int, val content: String)
    data class Target(val chapterIndex: Int, val chapterPosition: Int)

    /** Returns only a unique best match, leaving ambiguous targets for user confirmation. */
    fun locate(anchor: TextProcessAnchor, candidates: List<Candidate>): Target? {
        val matches = candidates.flatMap { candidate ->
            occurrences(candidate.content, anchor.selectedText).map { position ->
                Match(Target(candidate.chapterIndex, position), score(candidate, position, anchor))
            }
        }
        val best = matches.maxByOrNull { it.score } ?: return null
        return best.target.takeIf { target -> matches.count { it.score == best.score } == 1 }
    }

    private fun occurrences(content: String, text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Int>()
        var position = content.indexOf(text)
        while (position >= 0) {
            result += position
            position = content.indexOf(text, position + text.length)
        }
        return result
    }

    private fun score(candidate: Candidate, position: Int, anchor: TextProcessAnchor): Int {
        val end = position + anchor.selectedText.length
        var score = 0
        if (anchor.contextBefore.isNotBlank() &&
            candidate.content.substring(0, position).endsWith(anchor.contextBefore)
        ) score += 4
        if (anchor.contextAfter.isNotBlank() &&
            candidate.content.substring(end).startsWith(anchor.contextAfter)
        ) score += 4
        return score
    }

    private data class Match(val target: Target, val score: Int)
}
