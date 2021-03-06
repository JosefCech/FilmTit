package cz.filmtit.core.model

import cz.filmtit.core.model.data.{TranslationPair, Chunk}

/**
 * Interface for translation pair searchers, based on a local database,
 * external services, etc.
 *
 * @author Joachim Daiber
 */

abstract class TranslationPairSearcher(
  val l1: Language,
  val l2: Language,
  readOnly: Boolean = true
) {

  /**
   * Get the correct chunk for the language from the translation pair.
   */
  def chunkForLanguage(pair: TranslationPair, language: Language): Chunk = {
    if (language equals l1)
      pair.chunkL1
    else
      pair.chunkL2
  }


  /**
   * Retrieve a list of candidate translation pairs from a database or
   * service.
   */
  def candidates(chunk: Chunk, language: Language): List[TranslationPair]

}
