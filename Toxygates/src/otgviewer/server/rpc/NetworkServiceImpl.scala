package otgviewer.server.rpc

import t.viewer.shared.mirna.MirnaSource
import t.viewer.shared.TimeoutException
import t.platform.mirna.MiRDBConverter
import t.viewer.server.Configuration

class NetworkServiceImpl extends t.viewer.server.rpc.NetworkServiceImpl
  with OTGServiceServlet {

  lazy val dataTable = try {
    val file = s"$mirnaDir/mirdb_filter.txt"
    val t = new MiRDBConverter(file).makeTable
    println(s"Read ${t.size} miRNA targets from $file")
    Some(t)
  } catch {
    case e: Exception =>
      e.printStackTrace()
      None
  }

  override def localInit(config: Configuration) {
    super.localInit(config)
    dataTable
  }

}