package cz.filmtit.core.search.postgres.impl

import cz.filmtit.core.search.postgres.BaseSignatureStorage
import cz.filmtit.core.model._

import storage.Signature
import data.{Chunk}
import java.lang.String


/**
 * Simple exact signature based translation pair storage using the
 * first letters of words in the chunk as a signature for indexing.
 *
 * @author Joachim Daiber
 */

class FirstLetterStorage(l1: Language, l2: Language, readOnly: Boolean = true)
  extends BaseSignatureStorage(l1, l2, TranslationSource.InternalExact,
    "sign_firstletter", readOnly = readOnly) {

  /**
   * Use the lowercased first letter of each word in the sentence as the signature.
   */
  override def signature(chunk: Chunk, language: Language): Signature = {
    val tokens: Array[String] = chunk.surfaceform.split("[ ,.?!-]") filter (_ != "")

    tokens map {
      token =>
        token match {
          case Patterns.number() => '0'
          case _ => {
            token.take(
              tokens.size match {
                case 1 => 4
                case 2 => 3
                case 3 => 2
                case _ => 1
              }
            ).toLowerCase
          }
        }
    } mkString (" ")
  }

  override def name: String = "Translation pair storage using the first letter of every word as an index."

}



