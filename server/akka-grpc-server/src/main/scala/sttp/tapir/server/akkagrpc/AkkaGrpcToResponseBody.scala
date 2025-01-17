package sttp.tapir.server.akkagrpc

import akka.grpc.internal.AbstractGrpcProtocol
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.HasHeaders
import sttp.tapir.internal.charset
import sttp.tapir.server.akkahttp.AkkaResponseBody
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.{Charset, StandardCharsets}
import scala.concurrent.ExecutionContext

private[akkagrpc] class AkkaGrpcToResponseBody(implicit m: Materializer, ec: ExecutionContext)
    extends ToResponseBody[AkkaResponseBody, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): AkkaResponseBody =
    Right(
      overrideContentTypeIfDefined(
        rawValueToResponseEntity(bodyType, formatToContentType(format, charset(bodyType)), headers.contentLength, v),
        headers
      )
    )

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): AkkaResponseBody = ???

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AkkaStreams]
  ): AkkaResponseBody = ???

  private def rawValueToResponseEntity[CF <: CodecFormat, R](
      bodyType: RawBodyType[R],
      ct: ContentType,
      contentLength: Option[Long],
      r: R
  ): ResponseEntity = {
    bodyType match {
      case RawBodyType.StringBody(charset) => ???
      case RawBodyType.ByteArrayBody       => HttpEntity(ct, encodeDataToFrameBytes(ByteString(r)))
      case RawBodyType.ByteBufferBody      => HttpEntity(ct, encodeDataToFrameBytes(ByteString(r)))
      case RawBodyType.InputStreamBody     => ???
      case RawBodyType.FileBody            => ???
      case m: RawBodyType.MultipartBody    => ???
    }
  }

  private def formatToContentType(format: CodecFormat, charset: Option[Charset]): ContentType = {
    format match {
      case CodecFormat.Json()        => ContentTypes.`application/json`
      case CodecFormat.TextPlain()   => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.TextHtml()    => MediaTypes.`text/html`.withCharset(charsetToHttpCharset(charset.getOrElse(StandardCharsets.UTF_8)))
      case CodecFormat.OctetStream() => MediaTypes.`application/octet-stream`
      case CodecFormat.Zip()         => MediaTypes.`application/zip`
      case CodecFormat.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`
      case CodecFormat.MultipartFormData()  => MediaTypes.`multipart/form-data`
      case f =>
        val mt = if (f.mediaType.isText) charset.fold(f.mediaType)(f.mediaType.charset(_)) else f.mediaType
        parseContentType(mt.toString())
    }
  }

  private def parseContentType(ct: String): ContentType =
    ContentType.parse(ct).getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $ct"))

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())

  private def overrideContentTypeIfDefined[RE <: ResponseEntity](re: RE, headers: HasHeaders): RE = {
    headers.contentType match {
      case Some(ct) => re.withContentType(parseContentType(ct)).asInstanceOf[RE]
      case None     => re
    }
  }

  // TODO support for compressed body
  private def encodeDataToFrameBytes(data: ByteString): ByteString =
    AbstractGrpcProtocol.encodeFrameData(data, isCompressed = false, isTrailer = false)
}
