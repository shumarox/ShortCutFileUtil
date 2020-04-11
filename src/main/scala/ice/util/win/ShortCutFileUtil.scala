package ice.util.win

import java.io.FileNotFoundException

import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32._
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.{Function, Pointer, WString}

import scala.util.{Failure, Success, Using}

object ShortCutFileUtil {

  private object ShortCutFileImpl {

    initialize

    def initialize: Unit = {
      val result = Ole32.INSTANCE.CoInitializeEx(null, 0)
      if (W32Errors.FAILED(result)) throw new RuntimeException(s"CoInitializeEx failed $result")
    }

    def uninitialize: Unit = Ole32.INSTANCE.CoUninitialize

    private def createInstance(clsid: String, iid: String): PointerByReference = {
      val rclsid = Ole32Util.getGUIDFromString(clsid)
      val riid = Ole32Util.getGUIDFromString(iid)
      val pointerByReference = new PointerByReference
      val result = Ole32.INSTANCE.CoCreateInstance(rclsid, null, WTypes.CLSCTX_LOCAL_SERVER, riid, pointerByReference)
      if (W32Errors.FAILED(result)) throw new RuntimeException(s"CoCreateInstance failed $result")
      pointerByReference
    }

    private def invokeNoCheck(pointer: Pointer, args: AnyRef*): Int =
      Function.getFunction(pointer, Function.ALT_CONVENTION).invokeInt(args.toArray)

    private def invoke(name: String, pointer: Pointer, args: AnyRef*): Int = {
      val result = invokeNoCheck(pointer, args: _*)

      if (W32Errors.FAILED(result)) {
        val message = Kernel32Util.formatMessageFromLastErrorCode(result)

        result match {
          case 0x80070002 | 0x80070003 | 0x80070057 =>
            throw new FileNotFoundException(s"$name failed 0x${result.toHexString}. $message")
          case 0x80070005 | 0x80070020 =>
            throw new SecurityException(s"$name failed 0x${result.toHexString}. $message")
          case _ =>
            throw new RuntimeException(s"$name failed 0x${result.toHexString}. $message")
        }
      }

      result
    }

    class IUnknown(pointerByReference: PointerByReference, functionCount: Int = 3) extends AutoCloseable {
      protected val interfacePointer: Pointer = pointerByReference.getValue
      protected val vTable: Array[Pointer] = new Array[Pointer](functionCount)

      interfacePointer.getPointer(0).read(0, vTable, 0, vTable.length)

      def queryInterface(riid: REFIID, obj: PointerByReference): Unit =
        invoke("QueryInterface", vTable(0), interfacePointer, riid, obj)

      def addRef: Int = invokeNoCheck(vTable(1), interfacePointer)

      def release: Int = invokeNoCheck(vTable(2), interfacePointer)

      override def close: Unit = release
    }

    class IShellLink(pointerByReference: PointerByReference) extends IUnknown(pointerByReference, 21) {

      def this() = this(createInstance("{00021401-0000-0000-C000-000000000046}", "{000214F9-0000-0000-C000-000000000046}"))

      def persistFile: IPersistFile = {
        val riid = Ole32Util.getGUIDFromString("{0000010B-0000-0000-C000-000000000046}")
        val pointerByReference = new PointerByReference

        queryInterface(new REFIID(riid.getPointer), pointerByReference)

        new IPersistFile(pointerByReference)
      }

      def path: String = {
        val charArray = new Array[Char](1000)
        invoke("GetPath", vTable(3), interfacePointer, charArray, Integer.valueOf(charArray.length), null, Integer.valueOf(0))
        new String(charArray, 0, charArray.indexOf('\u0000'))
      }

      def path_=(path: String): Unit = invoke("SetPath", vTable(20), interfacePointer, new WString(path))

      def description: String = {
        val charArray = new Array[Char](1000)
        invoke("GetDescription", vTable(6), interfacePointer, charArray, Integer.valueOf(charArray.length))
        new String(charArray, 0, charArray.indexOf('\u0000'))
      }

      def description_=(name: String): Unit = invoke("SetDescription", vTable(7), interfacePointer, new WString(name))

      def arguments: String = {
        val charArray = new Array[Char](1000)
        invoke("GetArguments", vTable(10), interfacePointer, charArray, Integer.valueOf(charArray.length))
        new String(charArray, 0, charArray.indexOf('\u0000'))
      }

      def arguments_=(arguments: String): Unit = invoke("SetArguments", vTable(11), interfacePointer, new WString(arguments))

      def workingDirectory: String = {
        val charArray = new Array[Char](1000)
        invoke("GetWorkingDirectory", vTable(8), interfacePointer, charArray, Integer.valueOf(charArray.length))
        new String(charArray, 0, charArray.indexOf('\u0000'))
      }

      def workingDirectory_=(dir: String): Unit =
        invoke("SetWorkingDirectory", vTable(9), interfacePointer, new WString(dir))
    }

    class IPersistFile(pointerByReference: PointerByReference) extends IUnknown(pointerByReference, 9) {

      def load(fileName: String): Unit =
        invoke("Load", vTable(5), interfacePointer, new WString(fileName), Integer.valueOf(0))

      def save(fileName: String): Unit =
        invoke("Save", vTable(6), interfacePointer, new WString(fileName), new java.lang.Boolean(true))
    }

  }

  import ShortCutFileImpl._

  def createShortCut(fileName: String, linkPath: String, desc: String, args: String, workDir: String): Unit = {
    if (!fileName.endsWith(".lnk")) throw new IllegalArgumentException("ショートカットの拡張子が.lnkではありません。")

    Using(new IShellLink) { shellLink =>
      Using(shellLink.persistFile) { persistFile =>
        shellLink.path = linkPath
        shellLink.description = desc
        shellLink.arguments = args
        shellLink.workingDirectory = workDir

        persistFile.save(fileName)
      }
    }.flatten match {
      case Success(_) =>
      case Failure(f) => throw f
    }
  }

  def getLinkPath(shortCutFileName: String): String = {
    Using(new IShellLink) { shellLink =>
      Using(shellLink.persistFile) { persistFile =>
        persistFile.load(shortCutFileName)
        shellLink.path
      }
    }.flatten match {
      case Success(name) => name
      case Failure(f) => throw f
    }
  }

  def getArguments(shortCutFileName: String): String = {
    Using(new IShellLink) { shellLink =>
      Using(shellLink.persistFile) { persistFile =>
        persistFile.load(shortCutFileName)
        shellLink.arguments
      }
    }.flatten match {
      case Success(name) => name
      case Failure(f) => throw f
    }
  }
}

