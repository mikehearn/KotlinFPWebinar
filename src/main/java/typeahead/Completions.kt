package typeahead

import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

data class NGram(val words: String, val freq: Int = -1) : Comparable<NGram> {
    override fun compareTo(other: NGram): Int {
        val x = words.compareTo(other.words)
        if (x != 0 || freq == -1)
            return x
        else
            return freq.compareTo(other.freq)
    }
}

fun String.pairwise() = mapIndexed { index, char -> if (index + 1 <= lastIndex) "$char${this[index + 1]}" else "" }.dropLast(1)

fun NavigableSet<NGram>.complete(input: String): List<String> {
    // Convert input into a sequence of pairs of all possible prefixes and suffixes
    //
    // "a vect" -> [
    //    a vect :
    //    a vec  : t
    //    a ve   : ct
    // ]

    val canonicalisedInput = input.toLowerCase()
    val seq = sequenceOf(canonicalisedInput.lastIndex + 1 downTo 0).map {
        canonicalisedInput.substring(0, it) to canonicalisedInput.substring(it)
    }
    fun NavigableSet<NGram>.query(s: String) = tailSet(NGram(s)).headSet(NGram(s + Char.MAX_SURROGATE))
    val queried = seq.map { query(it.first) to it.second }

    // [
    //    [] -> ''
    //    [] -> 't'
    //    [ "a very common thing", .... ] -> 'ct'
    //    ...
    //    [.....] -> 'a vect'
    // ]

    val matches = queried.dropWhile { it.first.isEmpty() }
    val (candidates, remaining) = matches.first()

    val charPairs = if (remaining.length() == 1) listOf(remaining) else remaining.pairwise()
    val prefix = input.substring(0, input.length() - remaining.length())

    return candidates.filter { c ->
        val postfix = c.words.substring(prefix.length())
        charPairs.map { pair ->
            when {
                pair.length() == 1 -> postfix.contains(pair)
                else -> postfix.indexOf(pair[0]).let { it > 0 && it < postfix.lastIndexOf(pair[1]) }
            }
        }.all { it }
    }.sortedByDescending { it.freq }.map { it.words }
}

class Test {
    val ngrams = TreeSet(setOf(
            NGram("whatever"),
            NGram("a very common thing", 3),
            NGram("a very common phrase", 2),
            NGram("a very good call", 1),
            NGram("welcome to the", 0),
            NGram("welcome to my", 0)
    ))

    @Test
    fun pairs() {
        assertEquals(listOf("he", "el", "ll", "lo"), "hello".pairwise())
    }

    @Test
    fun basics() {
        assertEquals(listOf("welcome to my"), ngrams.complete("weltm"))
        assertEquals(listOf("a very common thing", "a very common phrase", "a very good call"), ngrams.complete("a ver"))
        assertEquals(listOf("a very common thing"), ngrams.complete("a vect"))
    }
}
