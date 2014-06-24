package t.admin.server

import scala.collection.JavaConversions._
import org.apache.commons.fileupload.FileItem
import com.google.gwt.user.server.rpc.RemoteServiceServlet
import gwtupload.server.UploadServlet
import javax.annotation.Nullable
import t.BaseConfig
import t.admin.client.MaintenanceService
import t.admin.shared.Progress
import t.TriplestoreConfig
import t.DataConfig
import t.TaskRunner
import t.BatchManager
import t.admin.shared.MaintenanceConstants._
import t.admin.shared.OperationResults
import t.util.TempFiles
import t.admin.shared.MaintenanceException
import otgviewer.server.Configuration
import javax.servlet.ServletConfig
import javax.servlet.ServletException
import t.admin.shared.Batch
import t.sparql.Batches
import t.admin.shared.Instance
import t.admin.shared.Platform

class MaintenanceServiceImpl extends RemoteServiceServlet with MaintenanceService {
  var baseConfig: BaseConfig = _
  
  @throws(classOf[ServletException])
  override def init(config: ServletConfig) {
    super.init(config)
    
    val conf = Configuration.fromServletConfig(config)
    baseConfig = conf.baseConfig
  }
    
  private def getAttribute[T](name: String) = 
    getThreadLocalRequest().getSession().getAttribute(name).asInstanceOf[T]
  
  private def setAttribute(name: String, x: AnyRef) = 
  	getThreadLocalRequest().getSession().setAttribute(name, x)
  
  private def setLastTask(task: String) = setAttribute("lastTask", task)  
  private def lastTask: String = getAttribute("lastTask")
  private def setLastResults(results: OperationResults) = setAttribute("lastResults", results)    
  private def lastResults: OperationResults = getAttribute("lastResults")    
  
  private def afterTaskCleanup() {
    val tc: TempFiles = getAttribute("tempFiles")
    if (tc != null) {
    	tc.dropAll()
    }
    UploadServlet.removeSessionFileItems(getThreadLocalRequest())
    TaskRunner.shutdown()
  }
  
  private def beforeTaskCleanup() {
    UploadServlet.removeSessionFileItems(getThreadLocalRequest())
  }
  
  
  def addBatchAsync(title: String): Unit = {
	showUploadedFiles()
	if (TaskRunner.currentTask != None) {
	  throw new Exception("Another task is already in progress.")
	}
	val bm = new BatchManager(baseConfig) //TODO configuration parsing

    try {
      TaskRunner.start()
      setLastTask("Add batch")

      val tempFiles = new TempFiles()
      setAttribute("tempFiles", tempFiles)

      if (getFile(metaPrefix) == None) {
        throw new MaintenanceException("The metadata file has not been uploaded yet.")
      }
      if (getFile(niPrefix) == None) {
        throw new MaintenanceException("The normalized intensity file has not been uploaded yet.")
      }
      if (getFile(mas5Prefix) == None) {
        throw new MaintenanceException("The MAS5 normalized file has not been uploaded yet.")
      }
      if (getFile(callPrefix) == None) {
        throw new MaintenanceException("The calls file has not been uploaded yet.")
      }

      val metaFile = getAsTempFile(tempFiles, metaPrefix, metaPrefix, "tsv").get
      val niFile = getAsTempFile(tempFiles, niPrefix, niPrefix, "csv").get
      val mas5File = getAsTempFile(tempFiles, mas5Prefix, mas5Prefix, "csv").get
      val callsFile = getAsTempFile(tempFiles, callPrefix, callPrefix, "csv").get

      TaskRunner ++= bm.addBatch(title, metaFile.getAbsolutePath(),
        niFile.getAbsolutePath(),
        mas5File.getAbsolutePath(),
        callsFile.getAbsolutePath())
    } catch {
	  case e: Exception =>
	    afterTaskCleanup()
	    throw e	  
	}
  }

  def tryAddPlatform(): Unit = {    
    
  }

  def deleteBatchAsync(id: String): Unit = {
    if (TaskRunner.currentTask != None) {
      throw new Exception("Another task is already in progress.")
    }
    val bm = new BatchManager(baseConfig) //TODO configuration parsing
    try {
      TaskRunner.start()
      setLastTask("Delete batch")
      TaskRunner ++= bm.deleteBatch(id)
    } catch {
      case e: Exception =>
        afterTaskCleanup()
        throw e
    }
  }

  def tryDeletePlatform(id: String): Boolean = {
    false;
  }
  
  def getOperationResults(): OperationResults = {
    lastResults
  }

  def cancelTask(): Unit = {
    //TODO
    afterTaskCleanup()	
  }

  def getProgress(): Progress = {    
    val messages = TaskRunner.logMessages.toArray
    for (m <- messages) {
      println(m)
    }
    val p = TaskRunner.currentTask match {
      case None => new Progress("No task in progress", 0, true)
      case Some(t) => new Progress(t.name, t.percentComplete, false)
    }
    p.setMessages(messages)
    
    if (TaskRunner.currentTask == None) {
      setLastResults(new OperationResults(lastTask, 
          TaskRunner.errorCause == None, 
          TaskRunner.resultMessages.toArray))      
      afterTaskCleanup()
    }
    p    
  }
  
  def getBatches: Array[Batch] = {
    val bs = new Batches(baseConfig.triplestore)
    val ns = bs.numSamples
    bs.list.map(b => {
      val samples = ns.getOrElse(b, 0)
      new Batch(b, samples, Array[String]())
    }).toArray    
  }
  
  def getPlatforms: Array[Platform] = {
    Array()
  }
  
  def getInstances: Array[Instance] = {
    Array()
  }
  
  def updateBatch(b: Batch): Unit = {
    
  }

  /**
   * Retrive the last uploaded file with a particular tag.
   * @param tag the tag to look for.
   * @return
   */
  private def getFile(tag: String): Option[FileItem] = {       
    val items = UploadServlet.getSessionFileItems(getThreadLocalRequest());    
    if (items == null) {
      throw new MaintenanceException("No files have been uploaded yet.")
    }
    
    var item: FileItem = null
    for (fi <- items) {      
      if (fi.getFieldName().startsWith(tag)) {
        item = fi
      }
    }    
    if (item == null) {
      None
    } else {
      Some(item)
    }    
  }
  
  /**
   * Get an uploaded file as a temporary file.
   * Should be deleted after use.
   */
  private def getAsTempFile(tempFiles: TempFiles, tag: String, prefix: String, 
      suffix: String): Option[java.io.File] = {
    getFile(tag) match {
      case None => None
      case Some(fi) =>        
        val f = tempFiles.makeNew(prefix, suffix)        
        fi.write(f)
        Some(f)
    }
  }
  
  private def showUploadedFiles(): Unit = {
    val items = UploadServlet.getSessionFileItems(getThreadLocalRequest())
    if (items != null) {
      for (fi <- items) {
        System.out.println("File " + fi.getName() + " size "
          + fi.getSize() + " field: " + fi.getFieldName())
      }
    }    
  }
}