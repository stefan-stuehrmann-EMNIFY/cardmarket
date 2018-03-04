package uk.co.seansaville.cardmarket

import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.util.Random

case class Credentials(
  accessToken: String,
  accessSecret: String,
  appToken: String,
  appSecret: String
)

object Authentication {
  private def buildBaseString(request: String, realm: String): String = {
    request + "&" + URLEncoder.encode(realm, "UTF-8") + "&"
  }

  private def buildParameterString(oauthP: Map[String, String], urlP: Map[String, String]): String = {
    val merged = (oauthP.toSeq ++ urlP.toSeq).sorted
    val params = for {(name, value) <- merged} yield name + "=" + value
    URLEncoder.encode(params.mkString("&"), "UTF-8")
  }

  private def buildSignature(request: String, signingKey: String): String = {
    Base64.getEncoder.encodeToString(sha1Hash(request.getBytes, signingKey.getBytes))
  }

  private def buildSigningKey(credentials: Credentials): String = {
    val appSecret = URLEncoder.encode(credentials.appSecret, "UTF-8")
    val accessSecret = URLEncoder.encode(credentials.accessSecret, "UTF-8")
    appSecret + "&" + accessSecret
  }

  private def generateNonce(length: Int): String = {
    val randoms = for (_ <- 1 to length) yield Random.nextInt(16).toHexString
    randoms.mkString
  }

  private def sha1Hash(bytes: Array[Byte], secret: Array[Byte]): Array[Byte] = {
    val sha1 = Mac.getInstance("HmacSHA1")
    sha1.init(new SecretKeySpec(secret, sha1.getAlgorithm))
    sha1.doFinal(bytes)
  }

  private def urlToRealmAndParams(url: String): (String, Map[String, String]) = {
    // Split a URL into the base and, optionally, the parameters
    def splitUrl(url: String): (String, Option[String]) = {
      val regex = "(.*)\\?(.*)".r
      val matched = regex.findFirstMatchIn(url)
      matched match {
        case Some(m) => (m.group(1), Some(m.group(2)))
        case None    => (url, None)
      }
    }

    // Split a parameter into a (name, value) pair
    def splitParameter(param: String): (String, String) = {
      val regex = "(.*)=(.*)".r
      val regex(name, value) = param
      (name, value)
    }

    val (realm, params) = splitUrl(url)
    val parameterMap = params match {
      case None    => Map[String, String]()
      case Some(p) => p.split("&").map(splitParameter).toMap
    }
    (realm, parameterMap)
  }

  def buildOAuthHeader(creds: Credentials, url: String, request: String): String = {
    val nonce = generateNonce(32)
    val signatureMethod = "HMAC-SHA1"
    val timestamp = (System.currentTimeMillis() / 1000).toString
    val version = "1.0"

    // Split the URL into the base URL and its parameters
    val (realm, urlParameters) = urlToRealmAndParams(url)

    // Base string with the request type and the realm
    val baseString = buildBaseString(request, realm)

    // Build a map with all of the OAuth-specific parameters
    val oAuthParameters = Map("oauth_consumer_key" -> creds.appToken,
      "oauth_nonce" -> nonce,
      "oauth_signature_method" -> signatureMethod,
      "oauth_timestamp" -> timestamp,
      "oauth_token" -> creds.accessToken,
      "oauth_version" -> version)

    // Concatenate the OAuth parameters and the URL parameters into a single string
    val parameterString = buildParameterString(oAuthParameters, urlParameters)

    // Build the signing key and then generate the signature
    val signingKey = buildSigningKey(creds)
    val signature = buildSignature(baseString + parameterString, signingKey)

    "OAuth " +
      "realm=\"" + realm + "\", " +
      "oauth_version=\"" + version + "\", " +
      "oauth_timestamp=\"" + timestamp + "\", " +
      "oauth_nonce=\"" + nonce + "\", " +
      "oauth_consumer_key=\"" + creds.appToken + "\", " +
      "oauth_token=\"" + creds.accessToken + "\", " +
      "oauth_signature_method=\"" + signatureMethod + "\", " +
      "oauth_signature=\"" + signature + "\""
  }
}