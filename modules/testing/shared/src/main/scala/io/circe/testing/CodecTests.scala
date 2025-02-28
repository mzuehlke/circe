package io.circe.testing

import cats.instances.either._
import cats.kernel.Eq
import cats.kernel.laws.SerializableLaws
import cats.laws._
import cats.laws.discipline._
import io.circe.{ Codec, Decoder, Encoder, Json }
import org.scalacheck.{ Arbitrary, Prop, Shrink }
import org.typelevel.discipline.Laws

trait CodecLaws[A] {
  def decode: Decoder[A]
  def encode: Encoder[A]
  def from: Codec[A] = Codec.from(decode, encode)

  def codecRoundTrip(a: A): IsEq[Decoder.Result[A]] =
    encode(a).as(decode) <-> Right(a)

  def codecFromConsistency(json: Json, a: A): IsEq[
    (
      Decoder.Result[A],
      Decoder.Result[A],
      Decoder.AccumulatingResult[A],
      Decoder.AccumulatingResult[A],
      Json
    )
  ] = {
    def output(d: Decoder[A], e: Encoder[A]) = (
      d.apply(json.hcursor),
      d.tryDecode(json.hcursor),
      d.decodeAccumulating(json.hcursor),
      d.tryDecodeAccumulating(json.hcursor),
      e.apply(a)
    )

    output(decode, encode) <-> output(from, from)
  }

  def codecAccumulatingConsistency(json: Json): IsEq[Decoder.Result[A]] =
    decode(json.hcursor) <-> decode.decodeAccumulating(json.hcursor).leftMap(_.head).toEither
}

object CodecLaws {
  def apply[A](implicit decodeA: Decoder[A], encodeA: Encoder[A]): CodecLaws[A] = new CodecLaws[A] {
    val decode: Decoder[A] = decodeA
    val encode: Encoder[A] = encodeA
  }
}

trait CodecTests[A] extends Laws {
  def laws: CodecLaws[A]

  def codec(implicit
    arbitraryA: Arbitrary[A],
    shrinkA: Shrink[A],
    eqA: Eq[A],
    arbitraryJson: Arbitrary[Json],
    shrinkJson: Shrink[Json]
  ): RuleSet = new DefaultRuleSet(
    name = "codec",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) =>
      laws.codecRoundTrip(a)
    },
    "consistency with Codec.from" -> Prop.forAll { (json: Json, a: A) =>
      laws.codecFromConsistency(json, a)
    },
    "consistency with accumulating" -> Prop.forAll { (json: Json) =>
      laws.codecAccumulatingConsistency(json)
    },
    "decoder serializability" -> SerializableLaws.serializable(laws.decode),
    "encoder serializability" -> SerializableLaws.serializable(laws.encode)
  )

  def unserializableCodec(implicit
    arbitraryA: Arbitrary[A],
    shrinkA: Shrink[A],
    eqA: Eq[A],
    arbitraryJson: Arbitrary[Json],
    shrinkJson: Shrink[Json]
  ): RuleSet = new DefaultRuleSet(
    name = "codec",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) =>
      laws.codecRoundTrip(a)
    },
    "consistency with accumulating" -> Prop.forAll { (json: Json) =>
      laws.codecAccumulatingConsistency(json)
    }
  )
}

object CodecTests {
  def apply[A: Decoder: Encoder]: CodecTests[A] = new CodecTests[A] {
    val laws: CodecLaws[A] = CodecLaws[A]
  }
}
