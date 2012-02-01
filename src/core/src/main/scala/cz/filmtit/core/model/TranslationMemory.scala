package cz.filmtit.core.model

import cz.filmtit.core.model.Language._

/**
 * @author Joachim Daiber
 *
 *
 *
 */

trait TranslationMemory {

  def nBest(chunk: Chunk, mediaSource: MediaSource, language: Language,
            n: Int = 10): List[ScoredTranslationPair]

  def firstBest(chunk: Chunk, mediaSource: MediaSource, language: Language):
    ScoredTranslationPair

}