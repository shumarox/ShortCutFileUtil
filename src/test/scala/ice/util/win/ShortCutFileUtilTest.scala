package ice.util.win

import org.junit.Test
import org.hamcrest.MatcherAssert._
import org.hamcrest.CoreMatchers._

class ShortCutFileUtilTest {

  @Test
  def test(): Unit = {
    val fileName = """C:\tmp\あ - ショートカット.lnk"""
    val linkPath = """C:\tmp\あ"""
    val desc = """コメント"""
    val args = """引数1 引数2"""
    val workDir = """C:\tmp"""

    ShortCutFileUtil.createShortCut(fileName, linkPath, desc, args, workDir)

    val actualLinkPath = ShortCutFileUtil.getLinkPath(fileName)

    assertThat("linkPath", actualLinkPath, is(linkPath))

    val actualArgs = ShortCutFileUtil.getArguments(fileName)

    assertThat("arguments", actualArgs, is(args))
  }
}
