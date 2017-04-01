package bsnlp

import io.circe._
import io.circe.generic.auto._

object Wikidata {
  case class SiteLink(title: String, badges: List[String])

  sealed trait Value
  case class WikibaseEntityIdValue(`entity-type`: String, `numeric-id`: Long, id: String) extends Value
  case class MonolingualTextValue(text: String, language: String) extends Value
  case class StringValue(value: String) extends Value
  case class GlobeCoordinateValue(latitude: Double, longitude: Double, altitude: Option[Int], precision: Double, globe: String) extends Value
  case class QuantityValue(amount: String, unit: String) extends Value
  case class TimeValue(time: String, timezone: Int, before: Int, after: Int, precision: Int, calendarmodel: String) extends Value

  sealed trait Snak
  case class ValueSnak(property: String, datavalue: Value, datatype: String) extends Snak
  case class SomeValueSnak(property: String, datatype: String) extends Snak
  case class NoValueSnak(property: String, datatype: String) extends Snak

  case class LanguageValues(values: Map[String, String]) extends AnyVal
  case class LanguageValuesMany(values: Map[String, List[String]]) extends AnyVal

  case class Claim(id: String,
                   mainsnak: Snak,
                   `type`: String,
                   rank: String)

  case class Entry(id: String,
                   `type`: String,
                   labels: LanguageValues,
                   descriptions: LanguageValues,
                   aliases: LanguageValuesMany,
                   claims: Map[String, List[Claim]],
                   sitelinks: Map[String, SiteLink])

  private case class Language(language: String, value: String)

  implicit val decodeLanguageValues: Decoder[LanguageValues] = Decoder.instance { c =>
    c.as[Map[String, Language]].map(v => LanguageValues(v.mapValues(_.value)))
  }

  implicit val decodeLanguageValuesMany: Decoder[LanguageValuesMany] = Decoder.instance { c =>
    c.as[Map[String, List[Language]]].map(v =>
      LanguageValuesMany(v.mapValues(_.map(_.value))))
  }

  implicit val decodeSnak: Decoder[Snak] = Decoder.instance { c =>
    c.downField("snaktype").as[String].flatMap {
      case "value" => c.as[ValueSnak]
      case "somevalue" => c.as[SomeValueSnak]
      case "novalue" => c.as[NoValueSnak]
    }
  }

  implicit val decodeValue: Decoder[Value] = Decoder.instance { c =>
    val value = c.downField("value")
    c.downField("type").as[String].flatMap {
      case "monolingualtext" => value.as[MonolingualTextValue]
      case "wikibase-entityid" => value.as[WikibaseEntityIdValue]
      case "string" => value.as[String].map(StringValue)
      case "globecoordinate" => value.as[GlobeCoordinateValue]
      case "quantity" => value.as[QuantityValue]
      case "time" => value.as[TimeValue]
    }
  }
}
