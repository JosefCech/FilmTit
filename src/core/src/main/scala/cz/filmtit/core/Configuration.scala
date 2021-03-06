package cz.filmtit.core

import cz.filmtit.core.model.Language
import model.annotation.{ChunkAnnotation, Name}
import java.io.File
import scala.xml._

/**
 * Configuration file for the external files and databases required by the TM.
 *
 * @author Joachim Daiber
 */

object Configuration {

  private val XMLFile = XML.loadFile("configuration.xml")

  //Database:
  private val dbXML = XMLFile \ "database"
  val dbConnector: String = (dbXML \ "connector").text
  val dbUser: String =  (dbXML \ "user").text
  val dbPassword: String = (dbXML \ "password").text

  //Named entity recognition:
  val modelPath: String = (XMLFile \ "model_path").text
  val neRecognizers: Map[Language, List[Pair[ChunkAnnotation, String]]] = Map(
    Language.en -> List(
      (Name.Person,       modelPath+"en-ner-person.bin"),
      (Name.Place,        modelPath+"en-ner-location.bin"),
      (Name.Organization, modelPath+"en-ner-organization.bin")
    ),
    Language.cs -> List(

    )
  )

  //Indexing:
  private val importXML = XMLFile \ "import"
  val expectedNumberOfTranslationPairs = (importXML \ "expected_number_of_translationpairs").text.toInt

  private val heldoutXML = importXML \ "heldout"
  val heldoutSize = (heldoutXML \ "size").text.toDouble //percentage of all data
  val heldoutFile = new File((heldoutXML \ "path").text)

}
