package com.fujivibe.recipe

/**
 * What the user is shooting, as ticked tags, and the slots to fill. [locked] slots (slot to the recipe in them)
 * are left alone, and what they already cover counts as covered.
 */
data class SuggestRequest(
    val subjects: Set<String> = emptySet(),
    val light: Set<String> = emptySet(),
    val mood: Set<String> = emptySet(),
    val includeMonochrome: Boolean = true,
    /** Whether the user is bringing a diffusion filter; without one, recipes that lean on it are avoided. */
    val hasFilter: Boolean = true,
    val locked: Map<Int, Recipe> = emptyMap(),
    val slots: List<Int> = (2..7).toList(),
)

/** A recipe for a slot, with a short reason ("mountains, snow, hard light"). */
data class Pick(val slot: Int, val recipe: Recipe, val reason: String)

/** [gaps] are ticked tags that no recipe in the library is tagged for. */
data class Suggestion(val picks: List<Pick>, val gaps: List<String>)

/**
 * Picks recipes for the free slots from the ticked tags: plain overlap scoring, so the reason for each is
 * always something the user ticked. It rewards covering tags the earlier picks do not, keeps recipes on the
 * same film simulation apart, can reserve one black and white and one all-rounder, and leans away from
 * recipes whose `avoid` text names something the user ticked.
 */
object Recommender {
    private const val SUBJECT = 3.0
    private const val LIGHT = 2.0
    private const val MOOD = 1.0

    private class Candidate(val recipe: Recipe, val role: Role, val gain: Double, val matched: List<String>)

    private enum class Role { MONO, ALL_ROUNDER, MATCH }

    fun suggest(all: List<Recipe>, req: SuggestRequest): Suggestion {
        val free = req.slots.filter { it !in req.locked }
        val lockedRecipes = req.locked.values.toList()
        val pool = all.filter { r -> lockedRecipes.none { it.id == r.id } }.toMutableList()
        val chosen = mutableListOf<Candidate>()
        val covered = mutableSetOf<String>()
        lockedRecipes.forEach { covered += tagsOf(it, req).map { t -> t.first } }

        fun take(c: Candidate) {
            chosen += c
            pool.removeAll { it.id == c.recipe.id }
            covered += tagsOf(c.recipe, req).map { it.first }
        }

        fun candidate(r: Recipe, role: Role, bonus: Double = 0.0): Candidate {
            val tags = tagsOf(r, req)
            val gain = tags.sumOf { (key, weight) -> if (key in covered) weight * 0.25 else weight }
            val matched = tags.map { it.first }
            val sameSim = (chosen.map { it.recipe } + lockedRecipes).count { it.baseSimulation == r.baseSimulation }
            return Candidate(r, role, gain + bonus - penalty(r, req) - 1.5 * sameSim, matched)
        }

        if (free.isNotEmpty() && req.includeMonochrome && lockedRecipes.none { it.baseSimulation.isMonochrome }) {
            pool.filter { it.baseSimulation.isMonochrome }
                .map { candidate(it, Role.MONO, generality(it)) }
                .maxByOrNull { it.gain }?.let(::take)
        }
        if (free.size - chosen.size >= 3) {
            pool.filter { !it.baseSimulation.isMonochrome }
                .map { candidate(it, Role.ALL_ROUNDER, 3 * generality(it)) }
                .maxByOrNull { it.gain }?.let(::take)
        }
        while (chosen.size < free.size && pool.isNotEmpty()) {
            val next = pool.map { candidate(it, Role.MATCH, generality(it) * 0.2) }.maxByOrNull { it.gain } ?: break
            take(next)
        }

        // Best match first, so it lands in the lowest free slot (the easiest to reach on the dial).
        val ordered = chosen.withIndex().sortedWith(compareByDescending<IndexedValue<Candidate>> { it.value.gain }.thenBy { it.index }).map { it.value }
        val picks = ordered.zip(free).map { (c, slot) -> Pick(slot, c.recipe, reasonFor(c)) }
        return Suggestion(picks, gaps(all, req))
    }

    /** The ticked tags this recipe has, as a key and its weight. */
    private fun tagsOf(r: Recipe, req: SuggestRequest): List<Pair<String, Double>> =
        r.bestFor.subjects.filter { it in req.subjects }.map { "s:$it" to SUBJECT } +
            r.bestFor.light.filter { it in req.light }.map { "l:$it" to LIGHT } +
            r.bestFor.mood.filter { it in req.mood }.map { "m:$it" to MOOD }

    private fun generality(r: Recipe) = (r.bestFor.subjects.size + r.bestFor.light.size) * 0.15

    private fun penalty(r: Recipe, req: SuggestRequest): Double {
        var p = 0.0
        val avoid = r.avoid.map { it.lowercase() }
        if (avoid.isNotEmpty()) {
            req.subjects.forEach { tag -> if (avoid.any { it.contains(tag.substringBefore('-')) }) p += 2.5 }
            (req.light + req.mood).forEach { tag -> if (avoid.any { it.contains(tag.replace('-', ' ')) }) p += 2.5 }
        }
        if (!req.hasFilter) {
            p += when (r.filterNeed()) {
                FilterNeed.STRONGER -> 4.0
                FilterNeed.USE -> 1.0
                FilterNeed.NONE -> 0.0
            }
        }
        return p
    }

    private fun reasonFor(c: Candidate): String {
        val words = c.matched.map { tagLabel(it.substringAfter(':')).lowercase() }
        return when {
            words.isNotEmpty() -> words.take(3).joinToString(", ")
            c.role == Role.MONO -> "your black and white pick"
            c.role == Role.ALL_ROUNDER -> "a good all-rounder"
            else -> "adds variety"
        }
    }

    private fun gaps(all: List<Recipe>, req: SuggestRequest): List<String> {
        val subjects = all.flatMap { it.bestFor.subjects }.toSet()
        val light = all.flatMap { it.bestFor.light }.toSet()
        val mood = all.flatMap { it.bestFor.mood }.toSet()
        return (req.subjects.filter { it !in subjects } + req.light.filter { it !in light } + req.mood.filter { it !in mood })
            .map { "No recipe is tagged for ${tagLabel(it).lowercase()}." }
    }
}
