package otgviewer.server

import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.InternetAddress
import javax.mail.Message
import javax.mail.Address
import javax.mail.Transport

object Feedback {
  
  implicit def asAddressAr(xs: Array[InternetAddress]): Array[Address] = {
    xs.map(_.asInstanceOf[Address]).toArray
  }

  /**
   *  Send a feedback message, as well as some application state,
   *  by e-mail.
   */
  def send(user: String, email: String, message: String, userState: String): Unit = {
    val p = new Properties()
    p.setProperty("mail.smtp.host", "localhost")

    try {
      val s = Session.getInstance(p)
      val m = new MimeMessage(s)
      m.setFrom(new InternetAddress("root@nibio.go.jp"))

      m.setRecipients(Message.RecipientType.TO,
        InternetAddress.parse("johan@monomorphic.org,kenji@nibio.go.jp,y-igarashi@nibio.go.jp"))

      m.setSubject(s"[System message] Toxygates user feedback from $user")
      m.setText(s"Feedback from: $user <$email>\n\nMessage: $message\n\nUser state: $userState")
      Transport.send(m)
    } catch {
      case e: Exception =>
        println("Exception while trying to send email.")
        println(s"User: $user email: $email")
        println("Feedback:")
        println(message)
        throw e //re-throw to the GUI
    }
  }
}