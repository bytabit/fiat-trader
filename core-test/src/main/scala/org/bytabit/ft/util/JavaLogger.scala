/*
 * Copyright 2016 Steven Myers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.bytabit.ft.util

import java.util.logging

import akka.actor._
import akka.dispatch.RequiresMessageQueue
import akka.event.Logging._
import akka.event.{LoggerMessageQueueSemantics, LoggingAdapter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Makes the Akka `Logging` API available as the `log`
  * field, using `java.util.logging` as the backend.
  *
  * This trait does not require an `ActorSystem` and is
  * encouraged to be used as a general purpose Scala
  * logging API.
  *
  * For `Actor`s, use `ActorLogging` instead.
  */
trait JavaLogging {

  @transient
  protected lazy val log = new JavaLoggingAdapter {
    def logger = logging.Logger.getLogger(JavaLogging.this.getClass.getName)
  }
}

/**
  * `java.util.logging` logger.
  */
class JavaLogger extends Actor with RequiresMessageQueue[LoggerMessageQueueSemantics] {

  def receive = {
    case event@Error(cause, _, _, _) ⇒ log(logging.Level.SEVERE, cause, event)
    case event: Warning ⇒ log(logging.Level.WARNING, null, event)
    case event: Info ⇒ log(logging.Level.INFO, null, event)
    case event: Debug ⇒ log(logging.Level.CONFIG, null, event)
    case InitializeLogger(_) ⇒ sender() ! LoggerInitialized
  }

  @inline
  def log(level: logging.Level, cause: Throwable, event: LogEvent) {
    val logger = logging.Logger.getLogger(event.logSource)
    val record = new logging.LogRecord(level, String.valueOf(event.message))
    record.setLoggerName(logger.getName)
    record.setThrown(cause)
    record.setThreadID(event.thread.getId.toInt)
    record.setSourceClassName(event.logClass.getName)
    record.setSourceMethodName(null) // lost forever
    logger.log(record)
  }
}

trait JavaLoggingAdapter extends LoggingAdapter {

  def logger: logging.Logger

  /** Override-able option for asynchronous logging */
  def loggingExecutionContext: Option[ExecutionContext] = None

  def isErrorEnabled = logger.isLoggable(logging.Level.SEVERE)

  def isWarningEnabled = logger.isLoggable(logging.Level.WARNING)

  def isInfoEnabled = logger.isLoggable(logging.Level.INFO)

  def isDebugEnabled = logger.isLoggable(logging.Level.CONFIG)

  protected def notifyError(message: String) {
    log(logging.Level.SEVERE, null, message)
  }

  protected def notifyError(cause: Throwable, message: String) {
    log(logging.Level.SEVERE, cause, message)
  }

  protected def notifyWarning(message: String) {
    log(logging.Level.WARNING, null, message)
  }

  protected def notifyInfo(message: String) {
    log(logging.Level.INFO, null, message)
  }

  protected def notifyDebug(message: String) {
    log(logging.Level.CONFIG, null, message)
  }

  @inline
  def log(level: logging.Level, cause: Throwable, message: String) {
    val record = new logging.LogRecord(level, message)
    record.setLoggerName(logger.getName)
    record.setThrown(cause)
    updateSource(record)

    if (loggingExecutionContext.isDefined) {
      implicit val context = loggingExecutionContext.get
      Future(logger.log(record)).onFailure {
        case thrown: Throwable ⇒ thrown.printStackTrace()
      }
    } else
      logger.log(record)
  }

  // it is unfortunate that this workaround is needed
  private def updateSource(record: logging.LogRecord) {
    val stack = Thread.currentThread.getStackTrace
    val source = stack.find {
      frame ⇒
        val cname = frame.getClassName
        !cname.startsWith("akka.contrib.jul.") &&
          !cname.startsWith("akka.event.LoggingAdapter") &&
          !cname.startsWith("java.lang.reflect.") &&
          !cname.startsWith("sun.reflect.")
    }
    if (source.isDefined) {
      record.setSourceClassName(source.get.getClassName)
      record.setSourceMethodName(source.get.getMethodName)
    } else {
      record.setSourceClassName(null)
      record.setSourceMethodName(null)
    }
  }

}

